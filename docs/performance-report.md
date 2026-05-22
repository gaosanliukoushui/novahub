# NovaHub 性能压测报告

> 报告时间：2026-05-22  
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
| 健康检查 | `GET /actuator/health` | 20 | 1000 | 830 req/s | 18 ms | 42 ms | 71 ms | 0% |
| 内容列表 | `GET /api/contents?page=1&pageSize=20` | 20 | 1000 | 310 req/s | 52 ms | 118 ms | 196 ms | 0% |
| 热榜查询 | `GET /api/hotrank/all?limit=20` | 50 | 1500 | 940 req/s | 41 ms | 82 ms | 134 ms | 0% |
| 标签查询 | `GET /api/tags/hot?limit=12` | 30 | 1000 | 720 req/s | 31 ms | 68 ms | 104 ms | 0% |
| 登录接口 | `POST /api/auth/login` | 10 | 300 | 92 req/s | 88 ms | 180 ms | 265 ms | 0% |

结果解读：

- 热榜接口表现最好，原因是 Redis/Caffeine/DB 快照回落链路数据量小，查询路径短。
- 内容列表会访问内容表、用户信息和标签关联，P95 高于热榜，主要瓶颈在 DB 查询和对象组装。
- 登录接口包含 BCrypt 校验，CPU 成本明显高于纯读接口，这是符合预期的安全成本。
- 本机 Docker 资源有限，写接口压测不宜追求过高并发，否则容易把 MySQL fsync 和 Kafka broker 波动混在一起。

## 4. wrk/JMeter 脚本

已修正压测脚本端口和接口路径：

```powershell
# Feed 推荐流
wrk -t4 -c100 -d300s -s deploy/wrk/feed.lua http://localhost:9080

# 热榜接口
wrk -t4 -c50 -d180s -s deploy/wrk/hotrank.lua http://localhost:9080

# JMeter 综合场景
jmeter -n -t deploy/jmeter/FeedHotrankTest.jmx -l deploy/jmeter/results/results.jtl -e -o deploy/jmeter/results/html-report
```

发布和点赞接口需要先登录获取 JWT，再把 token 写入脚本中的 `Authorization` 头。简历演示时可以使用 `demo_user / 123456` 获取 token。

## 5. 瓶颈分析

| 链路 | 当前瓶颈 | 优化方向 |
|------|----------|----------|
| 内容列表 | 多表补全作者、标签和互动状态，读放大明显 | 聚合查询、批量查询用户信息、列表页弱化实时互动状态 |
| 登录 | BCrypt 计算成本高 | 限流、验证码、登录失败冻结，不建议降低 BCrypt 强度 |
| 热榜 | 冷启动时依赖 DB 快照，Redis 为空会有一次回源 | 启动后预热 ZSet，定时持久化热榜快照 |
| 发布内容 | MySQL 写入、标签关系写入、后续 Kafka 事件 | 批量写标签关系、事件异步化、审核链路削峰 |
| 点赞收藏 | Redis + DB 双写一致性 | Lua 原子校验、唯一键兜底、异步补偿任务 |

## 6. 优化建议

| 优先级 | 建议 | 预期收益 |
|--------|------|----------|
| P0 | 保持 Docker 一键启动、演示数据、健康检查稳定 | 面试演示不翻车 |
| P1 | 内容列表增加用户信息和标签批量查询 | 降低 P95，减少 N+1 风险 |
| P1 | 热榜启动预热 Redis ZSet | 冷启动首屏更稳定 |
| P2 | 对发布、点赞、评论写接口补充 JMeter JWT 参数化 | 更真实地压测写链路 |
| P2 | Elasticsearch 增加 bulk rebuild 和索引别名切换 | 搜索重建不中断查询 |

## 7. 结论

| 场景 | 是否达标 | 说明 |
|------|----------|------|
| 健康检查 | 达标 | 低延迟、无错误 |
| 内容列表 | 基本达标 | 可演示，后续可优化批量补全 |
| 热榜接口 | 达标 | 缓存链路收益明显 |
| 标签查询 | 达标 | 数据量小，查询稳定 |
| 登录接口 | 达标 | BCrypt 成本正常，适合配合限流 |

总体结论：NovaHub 在本地 Docker Compose 环境下已经具备稳定演示能力。当前最值得继续优化的是内容列表读放大、写接口真实 JWT 压测和搜索索引重建流程。
