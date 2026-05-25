# NovaHub 项目深度手册

## 1. 项目定位

NovaHub 是一个面向简历展示和面试讲解的内容社区项目。它不是单纯的 CRUD 后端，而是围绕“内容发布、互动、分发、热榜、搜索、推荐、监控”这几条典型社区链路，做了一套可以本地一键启动、可以 Docker 演示、也可以展开技术细节追问的全栈作品。

这个项目最适合这样理解：

- 产品侧，它是一套轻量内容社区。
- 架构侧，它模拟了高并发社区产品的核心技术问题。
- 工程侧，它强调可复现、可演示、可验证，而不是只停留在“代码能跑”。

## 2. 你能从这个项目看到什么

读完整个项目，应该能理解这些问题是怎么落地的：

- 用户如何注册、登录、鉴权、区分普通用户和管理员
- 内容如何发布、保存草稿、提交审核、上线展示
- 点赞、收藏、评论如何落库，如何做幂等和限流
- Feed、热榜、推荐流分别解决什么问题
- 为什么热榜适合 Redis ZSet，为什么推荐和热榜不是一回事
- 搜索为什么默认用 Elasticsearch `standard` analyzer，而不是一上来装 IK
- 写链路为什么要落 `event_outbox`
- 为什么项目要加 `traceId`、`requestId`、smoke 脚本、CI 和压测报告

## 3. 技术栈

### 3.1 后端

- Java 17
- Spring Boot 3.2.5
- MyBatis-Plus 3.5.7
- Spring Kafka 3.1.4
- JWT `jjwt 0.12.5`
- Knife4j + OpenAPI 3
- Caffeine 本地缓存
- Lettuce Redis 客户端

### 3.2 基础设施

- MySQL 8.0
- Redis 7
- Kafka 3.7
- Elasticsearch 8.13.4
- MinIO
- Nginx
- SkyWalking
- Logstash / Kibana

### 3.3 前端

- 静态 HTML / CSS / JavaScript
- 无 Node 构建链
- Hash 路由单页应用

这个前端选择很适合简历项目，因为部署稳定、启动简单、面试现场不容易翻车。

## 4. 模块划分

项目是 Maven 多模块结构，`nova-web` 作为统一启动入口，其他模块负责具体业务。

### 4.1 核心模块

- `nova-common`
  - 放公共能力：统一响应、异常、JWT、Redis、配置、限流、幂等、traceId、outbox
- `nova-user`
  - 负责注册登录、用户资料、关注关系、角色与权限
- `nova-content`
  - 负责内容发布、草稿、标签、审核、详情、列表、对象存储上传
- `nova-interaction`
  - 负责点赞、收藏、评论、回复
- `nova-feed`
  - 负责关注流、推荐流、热门流
- `nova-hotrank`
  - 负责热榜分数计算、Redis ZSet 排名、数据库快照回落、启动预热
- `nova-search`
  - 负责 Elasticsearch 索引、全文搜索、索引同步、重建
- `nova-monitor`
  - 负责 PV/UV、内容数据、活动统计、看板接口
- `nova-notify`
  - 负责通知、未读数、WebSocket 推送
- `nova-recommend`
  - 负责推荐结果、A/B 实验、协同过滤、内容推荐、曝光点击记录
- `nova-web`
  - 聚合 Controller、Spring Boot 启动类、静态前端、管理操作接口

### 4.2 关键入口类

- [AuthController](E:/我的项目/NovaHub/nova-user/src/main/java/com/novahub/user/controller/AuthController.java)
- [UserController](E:/我的项目/NovaHub/nova-user/src/main/java/com/novahub/user/controller/UserController.java)
- [ContentController](E:/我的项目/NovaHub/nova-content/src/main/java/com/novahub/content/controller/ContentController.java)
- [LikeController](E:/我的项目/NovaHub/nova-interaction/src/main/java/com/novahub/interaction/controller/LikeController.java)
- [CommentController](E:/我的项目/NovaHub/nova-interaction/src/main/java/com/novahub/interaction/controller/CommentController.java)
- [CollectController](E:/我的项目/NovaHub/nova-interaction/src/main/java/com/novahub/interaction/controller/CollectController.java)
- [FeedController](E:/我的项目/NovaHub/nova-feed/src/main/java/com/novahub/feed/controller/FeedController.java)
- [HotRankController](E:/我的项目/NovaHub/nova-hotrank/src/main/java/com/novahub/hotrank/controller/HotRankController.java)
- [SearchController](E:/我的项目/NovaHub/nova-search/src/main/java/com/novahub/search/controller/SearchController.java)
- [RecommendController](E:/我的项目/NovaHub/nova-recommend/src/main/java/com/novahub/recommend/controller/RecommendController.java)
- [DashboardController](E:/我的项目/NovaHub/nova-monitor/src/main/java/com/novahub/monitor/controller/DashboardController.java)
- [AdminOpsController](E:/我的项目/NovaHub/nova-web/src/main/java/com/novahub/web/controller/AdminOpsController.java)

## 5. 数据模型

数据库脚本位于：

- [001_initial_schema.sql](E:/我的项目/NovaHub/db/sql/001_initial_schema.sql)
- [002_recommend_schema.sql](E:/我的项目/NovaHub/db/sql/002_recommend_schema.sql)
- [003_demo_data.sql](E:/我的项目/NovaHub/db/sql/003_demo_data.sql)
- [004_outbox_schema.sql](E:/我的项目/NovaHub/db/sql/004_outbox_schema.sql)

### 5.1 用户与权限

- `sys_user`
- `sys_role`
- `sys_permission`
- `sys_user_role`
- `sys_role_permission`
- `sns_follow`

这一块解决的是“谁能做什么”。项目里至少区分两类角色：

- `ROLE_USER`
- `ROLE_ADMIN`

管理员除了正常业务能力，还能触发热榜预热、搜索重建、演示数据重载、内容审核。

### 5.2 内容

- `content`
- `content_tag`
- `content_tag_rel`

内容表保存发布主体、正文、媒体、状态、审核状态和互动计数。项目里同时支持：

- 草稿
- 提交审核
- 已发布

### 5.3 互动

- `content_like`
- `content_collect`
- `collect_folder`
- `content_comment`
- `content_view`

这一层把点赞、收藏、评论、浏览从内容主表中拆开，既方便业务扩展，也便于统计与异步消费。

### 5.4 热榜与监控

- `content_stats`
- `hot_content_record`

`content_stats` 是热榜的数据库快照基础。Redis 丢了或者刚启动时，可以靠它回填热榜。

### 5.5 推荐与实验

- 推荐相关表在 [002_recommend_schema.sql](E:/我的项目/NovaHub/db/sql/002_recommend_schema.sql)
- 包含实验、桶、用户推荐行为等数据

### 5.6 可靠事件

- `event_outbox`

这是工程化很重要的一张表。核心作用不是“新增一个表”，而是解决：

- 业务主事务已经提交，但 Kafka 暂时不可用怎么办
- 事件发送失败后如何补偿
- 如何记录事件状态、重试次数和失败原因

## 6. 接口分层和请求路径

NovaHub 的典型请求路径是：

1. 浏览器访问 `http://localhost:8089/`
2. Nginx 转发 API 到 `nova-app`
3. `nova-web` 聚合各业务模块的 Controller
4. Service 层执行业务逻辑
5. MySQL / Redis / Kafka / ES / MinIO 参与读写

实际访问地址：

- 前端首页：`http://localhost:8089/`
- Dashboard：`http://localhost:8089/#/dashboard`
- Admin：`http://localhost:8089/#/admin`
- API 文档：`http://localhost:8089/doc.html`
- 健康检查：`http://localhost:9080/actuator/health`

## 7. 核心链路

### 7.1 登录链路

入口：

- [AuthController](E:/我的项目/NovaHub/nova-user/src/main/java/com/novahub/user/controller/AuthController.java)

关键点：

- 用户名密码校验
- BCrypt 密码比对
- 登录成功后生成 JWT
- 后续请求从 Header 中解析身份

为什么面试里值得讲：

- 登录不是性能最高的接口，因为 BCrypt 本身就是 CPU 密集型
- 登录接口通常要配限流、失败次数控制、防暴力破解

### 7.2 内容发布链路

核心实现：

- [ContentServiceImpl.publish](E:/我的项目/NovaHub/nova-content/src/main/java/com/novahub/content/service/impl/ContentServiceImpl.java:62)

这条链路做了几件事：

- 校验当前用户
- 做用户维度发布频控
- 写入 `content`
- 写入 `content_tag_rel`
- 对“提交审核”的内容写入 `event_outbox`
- 后续异步扩展 Kafka 事件

这里最值得讲的点：

- 发布接口不仅是“insert 一条内容”
- 它同时要考虑频控、标签关系、审核状态、异步事件
- 项目里把提交审核事件先落 outbox，是为了让主事务和消息投递更可靠

### 7.3 内容详情链路

核心实现：

- [ContentServiceImpl.getById](E:/我的项目/NovaHub/nova-content/src/main/java/com/novahub/content/service/impl/ContentServiceImpl.java:141)

这里用了典型的缓存保护策略：

- Redis 缓存内容详情
- 空值缓存防止缓存穿透
- 短锁防止并发回源
- 失败时允许回源数据库

为什么值得讲：

- 这是非常典型的高频读接口设计
- 面试官很容易顺着问缓存穿透、击穿、雪崩和一致性

### 7.4 内容列表链路

核心实现：

- [ContentServiceImpl.getPage](E:/我的项目/NovaHub/nova-content/src/main/java/com/novahub/content/service/impl/ContentServiceImpl.java:256)

当前项目已经做了一个很关键的优化：避免列表页明显的 N+1 查询。

具体做法：

- 先查内容分页
- 批量补作者信息
- 批量补标签信息
- 批量补当前用户互动状态

这部分非常适合写进简历，因为它体现了你不只是会写业务，还会关注列表读放大问题。

### 7.5 点赞 / 收藏 / 评论链路

涉及模块：

- `nova-interaction`

典型能力：

- 幂等控制
- 滑动窗口限流
- Redis 交互状态快速判断
- MySQL 唯一键兜底
- 异步写热榜统计事件

这一块最容易被问的问题：

- 为什么要同时有 Redis 状态和数据库唯一约束
- 为什么点赞不能只靠前端防重
- 为什么写链路比读链路更适合强调幂等和限流

### 7.6 Feed 链路

核心实现：

- [FeedService](E:/我的项目/NovaHub/nova-feed/src/main/java/com/novahub/feed/service/FeedService.java)

Feed 分三种：

- 关注流
- 推荐流
- 热门流

关注流思路：

- 发布内容后，把内容 fanout 到关注者 inbox
- inbox 存在 Redis ZSet 中

推荐流思路：

- 维护推荐池 `feed:recommend:list`
- 没命中时回源数据库兜底

热门流思路：

- 直接复用热榜结果

这里最值得讲的取舍：

- 社区产品里，Feed 不等于推荐
- 推荐是候选生成与排序问题
- Feed 更偏“内容分发与读取形态”

项目里还实现了“大 V 限流采样”的思路：

- 当粉丝数太大时，不直接全量 fanout
- 只对部分粉丝做采样推送，避免瞬时写扩散

### 7.7 热榜链路

核心实现：

- [HotRankService](E:/我的项目/NovaHub/nova-hotrank/src/main/java/com/novahub/hotrank/service/HotRankService.java)

热榜为什么适合 Redis ZSet：

- 需要按分数排序
- 需要高频更新
- 需要快速取 TopN

热度公式：

```text
delta = like * 3 + collect * 4 + comment * 5 + view * 1
newScore = (currentScore + delta) * 0.95 ^ hoursElapsed
```

热榜链路的关键工程点：

- Redis ZSet 存热度排序
- `content_stats` 做快照
- Caffeine 做进程内 L1 缓存
- 应用启动自动预热热榜
- 管理员可手动触发预热

这里的亮点不只是“会写公式”，而是你能讲清楚：

- 为什么要做时间衰减
- 为什么要有数据库快照兜底
- 为什么要加一层 Caffeine

### 7.8 搜索链路

核心类：

- [SearchService](E:/我的项目/NovaHub/nova-search/src/main/java/com/novahub/search/service/SearchService.java)
- [IndexService](E:/我的项目/NovaHub/nova-search/src/main/java/com/novahub/search/service/IndexService.java)
- [IndexSyncService](E:/我的项目/NovaHub/nova-search/src/main/java/com/novahub/search/service/IndexSyncService.java)
- [SearchIndexConsumer](E:/我的项目/NovaHub/nova-search/src/main/java/com/novahub/search/kafka/SearchIndexConsumer.java)

当前默认策略：

- 使用 Elasticsearch 内置 `standard` analyzer
- 保留 `keyword` 子字段
- 不强依赖 IK 插件

这么做的原因很实际：

- 官方 ES 镜像默认没有 IK
- 简历项目的首要目标是稳定启动和可演示
- 后续如果要强化中文召回，再换自定义镜像更合理

项目还补了管理端搜索重建入口：

- `POST /api/admin/search/rebuild`

这是一个很好的工程化展示点，因为它说明你考虑过：

- 索引重建
- 离线补数
- 后台运维操作

### 7.9 推荐链路

核心实现：

- [RecommendServiceImpl](E:/我的项目/NovaHub/nova-recommend/src/main/java/com/novahub/recommend/service/impl/RecommendServiceImpl.java)

推荐结果来源不是单一算法，而是混合策略：

- 协同过滤
- 基于内容
- 热门兜底
- A/B 实验权重

推荐主流程：

1. 判断用户与实验桶
2. 生成候选集
3. 做过滤
4. 分页
5. 加载内容详情
6. 记录曝光事件与点击事件

为什么这部分对简历有价值：

- 它展示了你不只会写 REST 接口
- 你能讲推荐系统的基础结构：候选生成、过滤、排序、实验、埋点

### 7.10 看板与埋点

核心模块：

- `nova-monitor`

你可以把它理解成“项目可视化和业务观测层”。

用途：

- 统计 PV/UV
- 统计热门内容
- 展示 Dashboard 数据
- 为推荐曝光、点击等行为记录提供支撑

## 8. 通用工程能力

### 8.1 统一响应与异常

公共类位于 `nova-common`。

常见能力：

- 统一 `Result<T>`
- 统一分页对象
- 统一异常处理

这样做的好处：

- 前后端约定更清晰
- 错误码更可控
- 测试和文档更容易维护

### 8.2 鉴权与上下文

公共工具：

- `SecurityUtils`
- JWT 工具类

职责：

- 从 token 里取当前用户
- 在请求上下文中传递用户信息
- 清理线程上下文，避免串请求

### 8.3 traceId / requestId

核心类：

- [TraceIdFilter](E:/我的项目/NovaHub/nova-common/src/main/java/com/novahub/common/filter/TraceIdFilter.java)

能力：

- 接收上游传来的 `X-Request-Id` / `X-Trace-Id`
- 如果没有就自动生成
- 写入 MDC
- 回写到响应头

这部分很适合回答“线上怎么排查问题”。

### 8.4 限流

核心切面：

- [RateLimitAspect](E:/我的项目/NovaHub/nova-common/src/main/java/com/novahub/common/ratelimit/RateLimitAspect.java)

策略：

- 滑动窗口限流
- 注解式接入

应用场景：

- 发布内容
- 点赞
- 评论

### 8.5 幂等

核心切面：

- [IdempotentAspect](E:/我的项目/NovaHub/nova-common/src/main/java/com/novahub/common/idempotent/IdempotentAspect.java)

策略：

- Redis 锁幂等
- 用户维度 key 隔离
- 数据库唯一约束兜底

这类题在面试里非常常见。你可以直接拿本项目举例：

- 为什么要加幂等
- Redis key 怎么设计
- 错误的 key 设计会带来什么问题

### 8.6 outbox

核心服务：

- [EventOutboxService](E:/我的项目/NovaHub/nova-common/src/main/java/com/novahub/common/service/EventOutboxService.java)

价值：

- 降低主事务和消息系统的耦合
- 为失败重试和补偿留下记录
- 让“可用性”变成可以讲清楚的工程能力

## 9. Docker 与部署结构

编排文件：

- [docker-compose.yml](E:/我的项目/NovaHub/deploy/docker-compose.yml)

默认服务：

- MySQL 主库
- MySQL 从库演示
- Redis
- Kafka
- Kafka UI
- Elasticsearch
- MinIO
- `novahub-app`
- Nginx
- SkyWalking
- Logstash
- Kibana

对简历项目来说，Docker 的意义不是“炫技”，而是：

- 面试现场能快速启动
- 别人拿到仓库就能复现
- 端口、账号、初始化脚本都可说明

## 10. 启动与验证

### 10.1 启动

```powershell
mvn -pl nova-web -am package -DskipTests
docker compose -f deploy/docker-compose.yml up -d --build
```

### 10.2 健康检查

```powershell
curl http://localhost:9080/actuator/health
```

返回 `UP` 说明后端基本启动完成。

### 10.3 浏览器验收

- `http://localhost:8089/`
- `http://localhost:8089/#/dashboard`
- `http://localhost:8089/#/admin`
- `http://localhost:8089/doc.html`

### 10.4 演示账号

- `demo_user / 123456`
- `demo_admin / 123456`

### 10.5 冒烟脚本

```powershell
./scripts/smoke.ps1
```

它会验证：

- 健康检查
- 登录
- 当前用户
- 内容流
- 内容详情
- 评论
- 热榜
- 标签
- 搜索入口

## 11. 测试与压测

### 11.1 自动化测试

建议优先看：

- `mvn -pl nova-web -am test`
- `CoreFlowIntegrationTest`

如果 Windows 下 Testcontainers 识别不到 Docker，需要开启：

```text
Expose daemon on tcp://localhost:2375 without TLS
```

然后执行：

```powershell
$env:DOCKER_HOST="tcp://localhost:2375"
mvn -pl nova-web -Dtest=CoreFlowIntegrationTest test
```

### 11.2 压测材料

参考：

- [performance-report.md](E:/我的项目/NovaHub/docs/performance-report.md)
- `deploy/jmeter/FeedHotrankTest.jmx`
- `deploy/wrk/`

本项目压测的价值，不在于“吹一个特别夸张的 QPS”，而在于：

- 你能区分公开读接口和登录态推荐接口
- 你能解释为什么写链路不能简单暴力压
- 你能讲瓶颈和优化方向

## 12. 当前项目的优势与边界

### 12.1 优势

- 功能闭环完整
- 技术栈覆盖面广
- Docker 启动稳定
- 前端可直接演示
- 架构和压测材料比较完整
- 有 outbox、限流、幂等、热榜、搜索、推荐这些能展开讲的点

### 12.2 边界

- 还是单体式多模块，不是微服务拆分
- 推荐算法是工程化演示版本，不是大规模生产推荐系统
- 搜索默认用 `standard` analyzer，中文召回不是最强方案
- 分库分表、强一致事务、复杂容灾没有真正落地到生产级别

这不是缺点，关键是你要在简历和面试里讲清楚：当前版本的目标是“可演示的高质量工程作品”，不是“真实线上千万级社区系统”。

## 13. 阅读顺序建议

如果你想快速吃透项目，按这个顺序看最省力：

1. [README.md](E:/我的项目/NovaHub/README.md)
2. [architecture.md](E:/我的项目/NovaHub/docs/architecture.md)
3. [project-deep-dive.md](E:/我的项目/NovaHub/docs/project-deep-dive.md)
4. [ContentServiceImpl.java](E:/我的项目/NovaHub/nova-content/src/main/java/com/novahub/content/service/impl/ContentServiceImpl.java)
5. [FeedService.java](E:/我的项目/NovaHub/nova-feed/src/main/java/com/novahub/feed/service/FeedService.java)
6. [HotRankService.java](E:/我的项目/NovaHub/nova-hotrank/src/main/java/com/novahub/hotrank/service/HotRankService.java)
7. [RecommendServiceImpl.java](E:/我的项目/NovaHub/nova-recommend/src/main/java/com/novahub/recommend/service/impl/RecommendServiceImpl.java)
8. [AdminOpsController.java](E:/我的项目/NovaHub/nova-web/src/main/java/com/novahub/web/controller/AdminOpsController.java)
9. [001_initial_schema.sql](E:/我的项目/NovaHub/db/sql/001_initial_schema.sql)
10. [performance-report.md](E:/我的项目/NovaHub/docs/performance-report.md)

## 14. 简历里怎么写

最重要的一句原则：

不要把 NovaHub 写成“做了一个论坛项目”。要写成“做了一套可演示、可验证、具备高并发常见治理能力的内容社区系统”。

### 14.1 一句话版

NovaHub：基于 Spring Boot 3、MyBatis-Plus、Redis、Kafka、Elasticsearch、MySQL 构建的内容社区系统，覆盖内容发布、互动、Feed、热榜、搜索、推荐、监控与 Docker 化演示链路。

### 14.2 简历项目描述版

负责设计并实现 NovaHub 内容社区项目，基于 Spring Boot 3 + MyBatis-Plus + Redis + Kafka + Elasticsearch 构建用户、内容、互动、Feed、热榜、搜索与推荐模块；通过 Redis ZSet + Caffeine + 数据库快照实现热榜系统，通过限流、幂等、JWT 鉴权、traceId/requestId、outbox 可靠事件和 Docker Compose 一键部署提升系统可用性与演示稳定性，并补充前端 SPA、自动化测试、压测报告和演示数据，形成可复现的全栈作品。

### 14.3 三条高质量 bullet

- 设计并实现多模块内容社区系统，完成注册登录、内容发布、草稿审核、点赞收藏评论、Feed 流、热榜、搜索、推荐和通知等核心链路，并通过静态 SPA 提供完整演示闭环。
- 针对高频读写场景引入 Redis 缓存、滑动窗口限流、接口幂等、数据库唯一约束、Redis ZSet 热榜、Caffeine 本地缓存和 outbox 可靠事件机制，提升热点接口稳定性与可扩展性。
- 搭建 Docker Compose 一键启动环境，补充演示数据、管理运维接口、健康检查、smoke 脚本、Testcontainers 集成测试和 JMeter 压测报告，使项目具备可复现、可验证、可面试讲解的工程化质量。

### 14.4 如果你投后端岗

重点讲：

- 内容发布链路
- 点赞评论幂等与限流
- 热榜计算
- 搜索同步
- outbox
- 压测和观测

### 14.5 如果你投全栈岗

在后端表述基础上补一句：

独立实现无 Node 构建依赖的静态 SPA 演示前端，支持首页内容流、详情评论、点赞收藏、用户中心、Dashboard 和 Admin 管理页，保证本地与 Docker 环境下开箱即用。

### 14.6 面试里建议怎么讲

建议按这条主线：

1. 先讲业务闭环：用户看内容、发内容、互动、看热榜、搜内容。
2. 再讲技术闭环：JWT、Redis、Kafka、ES、热榜、Feed、推荐。
3. 最后讲工程闭环：Docker、演示数据、测试、压测、traceId、管理后台。

这三层讲完，面试官一般就知道你不是“只会拼接口”的项目。
