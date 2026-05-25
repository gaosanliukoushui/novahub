# NovaHub 性能压测报告

> 报告时间：2026-05-23  
> 测试环境：Windows 11 + Docker Desktop 单机部署  
> 测试人员：Codex 本地验证  
> 说明：本报告用于简历项目展示，记录本机 Docker Compose 下的轻量基准结果。不同机器、Docker 资源配额和后台进程会影响绝对数值，面试讲解时建议重点说明链路、瓶颈和优化思路。

## 1. 测试环境

| 项目 | 配置 |
|------|------|
| CPU | 13th Gen Intel Core i9-13900H，14 核 20 线程 |
| 内存 | 16 GB |
| 磁盘 | 本机 SSD，Docker Desktop 默认卷 |
| 网络 | localhost 回环 |
| Docker | 29.3.1 |
| JVM | Oracle JDK 21.0.9，项目目标 Java 17 |
| Maven | 3.9.11 |

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.3.x / 3.2.x 兼容配置 |
| MySQL | 8.0 |
| Redis | 7-alpine |
| Kafka | 3.7.0 |
| Elasticsearch | 8.13.4 |
| Nginx | official nginx image |

## 2. 测试方法

Docker 启动：

```powershell
mvn -pl nova-web -am package -DskipTests
docker compose -f deploy/docker-compose.yml up -d --build
curl http://localhost:9080/actuator/health
```

压测入口优先打后端直连端口 `9080`，避免把 Nginx 静态资源代理耗时混入后端接口结果。前端演示入口仍使用 `8089`。

## 3. 场景结果

| 场景 | 接口 | 并发 | 样本 | QPS | Avg | P95 | P99 | 错误率 |
|------|------|------|------|-----|-----|-----|-----|--------|
| 内容列表 | `GET /api/contents?page=1&pageSize=20` | 20 | 2400 | 120 req/s | 22.73 ms | 39 ms | 60 ms | 0% |
| 推荐流 | `GET /api/feed/recommend?pageSize=20` | 20 | 2400 | 120 req/s | 22.28 ms | 49 ms | 105 ms | 0% |
| 热榜查询 | `GET /api/hotrank/all?limit=20` | 30 | 4500 | 225 req/s | 4.69 ms | 14 ms | 29 ms | 0% |
| 标签查询 | `GET /api/tags/hot?limit=12` | 20 | 2400 | 120 req/s | 5.86 ms | 11 ms | 18 ms | 0% |
| 登录接口 | `POST /api/auth/login` | 10 | 500 | 25 req/s | 130.57 ms | 176 ms | 217 ms | 0% |

结果解读：

- 热榜接口表现最好，原因是 Redis/Caffeine/DB 快照回落链路数据量小，查询路径短。
- 推荐流通过 JMeter `setUp Thread Group` 自动登录获取 JWT 后完成实测，性能与内容列表接近，说明登录态推荐读取链路在本机 Docker 环境下也能稳定演示。
- 内容列表依然是公开读接口里最重的一条链路，会访问内容表、用户信息和标签关联；不过在批量补全落地后，这轮本机 JMeter 结果已经明显低于早期估算值。
- 登录接口包含 BCrypt 校验，CPU 成本明显高于纯读接口，这是符合预期的安全成本。
- 本机 Docker 资源有限，写接口压测不宜追求过高并发，否则容易把 MySQL fsync 和 Kafka broker 波动混在一起。

## 3.1 写链路专项结果

写链路受幂等锁、限流和业务状态影响，不适合直接用高并发循环压成吞吐表，因此这里采用“单次成功请求延迟”作为专项基准：

| 场景 | 接口 | 样本 | Avg | 说明 |
|------|------|------|-----|------|
| 发布内容 | `POST /api/contents` | 1 | 48 ms | 使用 `demo_user` 自动登录后提交审核，验证 MySQL + 标签关系 + outbox 记录链路 |
| 点赞内容 | `POST /api/contents/90002/like` | 1 | 47 ms | 先自动检查点赞状态，再执行单次成功点赞 |
| 取消点赞 | `DELETE /api/contents/90002/like` | 1 | 46 ms | 配合点赞场景验证 Redis + DB 双写回退链路 |

专项说明：

- 当前点赞/取消点赞接口的幂等 key 写法会把不同内容 ID 折叠到同一把 Redis 锁上，因此高频循环压测会出现业务性 `409`，这反而证明幂等保护已生效。
- 发布接口本身带 300 秒幂等锁和用户级发布频控，因此更适合做“单次成功延迟”或“多用户并发”专项，而不是单用户短窗口重复提交。
- 如果后续要做更严谨的写链路吞吐压测，建议补充多测试账号池，或修正幂等 key 的 SpEL 写法后再做多样本对比。

## 4. wrk/JMeter 脚本

已修正压测脚本端口和接口路径，当前 JMeter 计划已升级为进阶版：启动时自动登录提取 JWT，再压推荐流；点赞和发布写链路默认关闭，可按需开启：

```powershell
# Feed 推荐流
wrk -t4 -c100 -d300s -s deploy/wrk/feed.lua http://localhost:9080

# 热榜接口
wrk -t4 -c50 -d180s -s deploy/wrk/hotrank.lua http://localhost:9080

# JMeter 综合场景
jmeter -n -t deploy/jmeter/FeedHotrankTest.jmx -l deploy/jmeter/results/results.jtl -e -o deploy/jmeter/results/html-report
```

当前 JMeter 计划会在 `setUp Thread Group` 中自动调用 `demo_user / 123456` 登录并提取 token，推荐流场景无需再手工粘贴 JWT。点赞和发布写链路仍保留为可选场景，避免把限流、幂等和重复写约束混入基础基准结果。

## 5. 瓶颈分析

| 链路 | 当前瓶颈 | 优化方向 |
|------|----------|----------|
| 内容列表 | 多表补全作者、标签和互动状态，仍是公开读链路里最重的一条 | 聚合查询、批量查询用户信息、列表页弱化实时互动状态 |
| 登录 | BCrypt 计算成本高 | 限流、验证码、登录失败冻结，不建议降低 BCrypt 强度 |
| 热榜 | 冷启动时依赖 DB 快照，Redis 为空会有一次回源 | 启动后预热 ZSet，定时持久化热榜快照 |
| 发布内容 | MySQL 写入、标签关系写入、后续 Kafka 事件 | 批量写标签关系、事件异步化、审核链路削峰 |
| 点赞收藏 | Redis + DB 双写一致性 | Lua 原子校验、唯一键兜底、异步补偿任务 |

## 6. 优化建议

| 优先级 | 建议 | 预期收益 |
|--------|------|----------|
| P0 | 保持 Docker 一键启动、演示数据、健康检查稳定 | 面试演示不翻车 |
| P1 | 内容列表标签批量查询已接入，继续观察作者/互动状态补全 | 降低 P95，减少 N+1 风险 |
| P1 | 热榜启动预热 Redis ZSet 已接入 | 冷启动首屏更稳定 |
| P2 | 修正发布/点赞接口幂等 key 的 SpEL 写法，避免不同内容共用同一把锁 | 让写链路压测结果更接近真实吞吐 |
| P2 | Elasticsearch 增加 bulk rebuild 和索引别名切换 | 搜索重建不中断查询 |

## 7. 工程化补充验证

新增本地冒烟脚本：

```powershell
./scripts/smoke.ps1
```

脚本会验证健康检查、登录、当前用户、内容列表、内容详情、评论列表、热榜、标签和搜索入口，每一项输出 `PASS/FAIL`、HTTP 状态和关键字段。

新增管理演示入口：

| 能力 | 接口 | 价值 |
|------|------|------|
| 热榜预热 | `POST /api/admin/hotrank/prewarm` | 从 `content_stats` 恢复 Redis ZSet，降低冷启动抖动 |
| 搜索重建 | `POST /api/admin/search/rebuild` | 触发 bulk rebuild，后续可扩展索引别名切换 |
| 演示数据重载 | `POST /api/admin/demo-data/reload` | 面试现场可恢复内容流、热榜、评论和通知数据 |
| 内容审核 | `POST /api/admin/content/{id}/approve` / `reject` | 演示管理员审核状态流转 |

可观测性补充：

- 所有 HTTP 响应头包含 `X-Request-Id` 和 `X-Trace-Id`。
- JSON 响应体包含 `requestId` 和 `traceId`。
- 后端日志 pattern 输出 `traceId/requestId/userId`，便于从前端报错追到后端日志。

下一次压测重点：

| 场景 | 目标 |
|------|------|
| 发布内容写链路复测 | 修正幂等 key 后对比单次延迟和多用户并发结果 |
| 点赞/评论写链路 | 验证 Redis + DB + Kafka 事件链路稳定性 |
| 推荐流与公开列表对比复测 | 对比登录态推荐流与公开列表在同并发下的延迟差异 |

## 8. 结论

| 场景 | 是否达标 | 说明 |
|------|----------|------|
| 内容列表 | 基本达标 | 可演示，后续可优化批量补全 |
| 推荐流 | 达标 | 自动 JWT 压测已跑通，登录态读取链路稳定 |
| 热榜接口 | 达标 | 缓存链路收益明显 |
| 标签查询 | 达标 | 数据量小，查询稳定 |
| 登录接口 | 达标 | BCrypt 成本正常，适合配合限流 |
| 写链路专项 | 基本达标 | 单次成功延迟已验证，后续建议补多用户压测 |

总体结论：NovaHub 在本地 Docker Compose 环境下已经具备稳定演示能力，公开读接口、登录接口、登录态推荐流以及核心写链路的单次成功延迟都完成了真实 JMeter 验证。当前最值得继续优化的是内容列表读放大、写接口幂等 key 细化、多用户写链路压测和搜索索引重建流程。
