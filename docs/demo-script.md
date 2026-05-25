# NovaHub 3 分钟面试演示稿

## 0:00 - 0:30 项目定位

NovaHub 是一个内容社区平台，覆盖注册登录、内容发布、草稿审核、点赞收藏评论、Feed、热榜、搜索、通知和数据看板。它不是单纯 CRUD，重点是把高并发内容产品常见的缓存、限流、幂等、异步事件、搜索索引和可复现部署串成一条完整链路。

## 0:30 - 1:20 产品闭环

打开 `http://localhost:8089/`，首页能看到内容流、热榜、热门标签和搜索。使用 `demo_user / 123456` 登录后，点击内容详情，可以完成点赞、收藏、评论；进入个人中心可以查看我的内容、收藏和通知；进入发布页可以保存草稿或提交审核。

## 1:20 - 2:10 工程深度

发布、点赞、评论等写接口使用 Redis 滑动窗口限流和幂等锁，避免重复提交和刷接口。热榜采用 Redis ZSet + Caffeine 本地缓存 + `content_stats` 数据库快照回落，冷启动时会自动预热 Redis。搜索默认使用 Elasticsearch `standard` analyzer，保证官方镜像开箱即用，同时保留 IK 分词扩展点。

## 2:10 - 2:40 可靠性与可观测性

提交审核会同步写入 `event_outbox`，Kafka 短暂不可用时仍能保留事件，后续由定时任务重试投递。每个请求都会生成 `requestId` 和 `traceId`，响应头、响应体和日志 MDC 中都能看到，排障时可以从前端报错一路追到后端日志。

## 2:40 - 3:00 工程化展示

README 提供 Docker Compose 一键启动、演示账号、端口冲突处理、演示数据重载和 smoke 脚本。`#/dashboard` 可以展示运营指标，`demo_admin` 打开 `#/admin` 后可以演示内容审核、热榜预热、搜索重建和演示数据重载。CI 会跑 Maven 测试、打包、UTF-8 编码检查和 Compose 配置校验。

## 截图与 GIF 拍摄清单

- `docs/screenshots/home.png`：首页内容流、热榜、标签。
- `docs/screenshots/detail.png`：内容详情、评论、点赞、收藏。
- `docs/screenshots/dashboard.png`：数据看板指标卡和趋势图。
- `docs/screenshots/admin.png`：管理员操作卡片。
- `docs/screenshots/demo-flow.gif`：登录、打开详情、评论点赞、切到 dashboard/admin 的完整路径。
