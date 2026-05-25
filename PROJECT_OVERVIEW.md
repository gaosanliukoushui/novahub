# NovaHub 项目总览

## 1. 项目一句话说明

NovaHub 是一个可演示、可验证、可展开技术细节讲解的内容社区系统，采用 Spring Boot 多模块架构，围绕用户、内容、互动、Feed、热榜、搜索、推荐、通知和监控构建完整闭环。

## 2. 当前项目状态

目前项目已经完成：

- Docker Compose 一键启动
- 静态 SPA 演示前端
- 演示账号与演示数据
- 内容发布、草稿、审核、点赞、收藏、评论、详情、列表
- Feed、热榜、搜索、推荐、通知、Dashboard、Admin
- 幂等、限流、traceId/requestId、outbox
- smoke 脚本、集成测试、JMeter 压测报告

## 3. 模块结构

| 模块 | 说明 |
|------|------|
| `nova-common` | 公共能力：响应、异常、JWT、Redis、配置、限流、幂等、traceId、outbox |
| `nova-user` | 用户、登录、关注、权限 |
| `nova-content` | 内容、标签、审核、上传 |
| `nova-interaction` | 点赞、收藏、评论 |
| `nova-feed` | 关注流、推荐流、热门流 |
| `nova-hotrank` | 热榜计算、快照回落、Redis ZSet |
| `nova-search` | Elasticsearch 搜索与索引同步 |
| `nova-monitor` | PV/UV、统计、数据看板 |
| `nova-notify` | 通知、未读数、WebSocket |
| `nova-recommend` | 推荐、A/B 实验、曝光点击 |
| `nova-web` | Spring Boot 启动入口、聚合接口、前端资源 |

## 4. 基础设施

- MySQL 8.0
- Redis 7
- Kafka 3.7
- Elasticsearch 8.13.4
- MinIO
- Nginx
- SkyWalking
- Logstash / Kibana

## 5. 推荐阅读顺序

1. [README.md](E:/我的项目/NovaHub/README.md)
2. [docs/project-deep-dive.md](E:/我的项目/NovaHub/docs/project-deep-dive.md)
3. [docs/architecture.md](E:/我的项目/NovaHub/docs/architecture.md)
4. [docs/performance-report.md](E:/我的项目/NovaHub/docs/performance-report.md)
5. [docs/resume-guide.md](E:/我的项目/NovaHub/docs/resume-guide.md)

## 6. 关键源码入口

- [ContentServiceImpl.java](E:/我的项目/NovaHub/nova-content/src/main/java/com/novahub/content/service/impl/ContentServiceImpl.java)
- [FeedService.java](E:/我的项目/NovaHub/nova-feed/src/main/java/com/novahub/feed/service/FeedService.java)
- [HotRankService.java](E:/我的项目/NovaHub/nova-hotrank/src/main/java/com/novahub/hotrank/service/HotRankService.java)
- [RecommendServiceImpl.java](E:/我的项目/NovaHub/nova-recommend/src/main/java/com/novahub/recommend/service/impl/RecommendServiceImpl.java)
- [AdminOpsController.java](E:/我的项目/NovaHub/nova-web/src/main/java/com/novahub/web/controller/AdminOpsController.java)
- [TraceIdFilter.java](E:/我的项目/NovaHub/nova-common/src/main/java/com/novahub/common/filter/TraceIdFilter.java)

## 7. 现在最值得讲的技术点

- 内容发布链路中的审核状态、限流和 outbox
- 点赞 / 评论链路中的幂等、限流和异步热榜统计
- Redis ZSet + Caffeine + `content_stats` 快照回落的热榜设计
- Feed 分发和大 V 粉丝采样推送
- Elasticsearch 默认 `standard` analyzer 的稳定性取舍
- traceId / requestId、smoke 脚本、压测报告带来的工程可信度

## 8. 简历使用建议

不要把项目写成“论坛练手项目”。更适合的表述是：

> 设计并实现基于 Spring Boot 3、Redis、Kafka、Elasticsearch 的内容社区系统，完成内容发布、互动、Feed、热榜、搜索、推荐和 Docker 化演示闭环，并通过幂等、限流、缓存、outbox、压测和自动化验证提升系统工程质量。

更完整版本参考：

- [docs/resume-guide.md](E:/我的项目/NovaHub/docs/resume-guide.md)

最后更新：2026-05-25
