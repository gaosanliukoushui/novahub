# NovaHub 内容社区平台

NovaHub 是一个面向简历展示的高并发内容社区项目，覆盖注册登录、内容发布、草稿、标签、点赞、收藏、评论、Feed、热榜、搜索、通知和监控等链路。项目重点不是“只写 CRUD”，而是把常见社区产品的核心工程问题串起来：缓存、限流、幂等、异步事件、搜索索引、热榜计算、Docker 可复现部署和前端可演示闭环。

## 项目亮点

| 方向 | 实现 |
|------|------|
| 用户与权限 | JWT 登录、用户资料、关注关系、角色权限基础模型 |
| 内容发布 | 草稿、提交审核、已发布内容列表、标签关联、浏览计数 |
| 互动链路 | 点赞、取消点赞、收藏、取消收藏、评论、回复、热门评论 |
| Feed 与推荐 | 关注流、热门流、推荐入口，支持 Kafka 异步扩展 |
| 热榜系统 | Redis ZSet + Caffeine 本地缓存 + DB 快照回落，支持可解释热度分 |
| 搜索系统 | Elasticsearch 8，默认使用内置 `standard` analyzer，保留扩展 IK 分词能力 |
| 工程治理 | Docker Compose 一键启动、演示数据、API 文档、压测报告、架构说明 |
| 前端演示 | 无 Node 构建依赖的静态单页应用，支持登录、内容详情、评论、点赞、收藏 |

## 一键启动

### 环境要求

- Docker Desktop
- JDK 17+
- Maven 3.8+

### 启动命令

```powershell
mvn -pl nova-web -am package -DskipTests
docker compose -f deploy/docker-compose.yml up -d --build
docker compose -f deploy/docker-compose.yml ps
```

### 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端演示页 | http://localhost:8089/ | 登录、内容流、发布、详情、评论、点赞收藏 |
| API 文档 | http://localhost:8089/doc.html | Knife4j / OpenAPI 文档 |
| 后端健康检查 | http://localhost:9080/actuator/health | 返回 `UP` 表示后端启动完成 |
| MySQL | `localhost:13306` | 用户 `novahub`，密码 `root123`，库 `nova_hub` |
| MinIO 控制台 | http://localhost:9001/ | `minioadmin / minioadmin` |
| Kafka UI | http://localhost:8090/ | 查看 Topic 和消息 |
| SkyWalking UI | http://localhost:8095/ | 链路追踪与监控 |

演示账号：

| 用户名 | 密码 | 用途 |
|--------|------|------|
| `demo_user` | `123456` | 普通用户演示：内容详情、评论、点赞、收藏、草稿 |
| `demo_admin` | `123456` | 管理员演示：权限模型和管理账号 |

## 演示数据

Fresh Docker volume 会自动执行 `db/sql/003_demo_data.sql`。如果你已经启动过项目，MySQL volume 不会重新执行初始化脚本，可以手动重复导入：

```powershell
docker exec novahub-mysql sh -c "mysql -unovahub -proot123 --default-character-set=utf8mb4 nova_hub < /docker-entrypoint-initdb.d/003_demo_data.sql"
```

导入后刷新 http://localhost:8089/，首页应能看到演示内容、热榜、标签和评论。

## 常见问题

### 端口 3306 被占用

项目 Docker Compose 已将 MySQL 映射为 `13306:3306`，避免和本机 MySQL 冲突。如果仍然报端口冲突，检查是否有其他容器占用了 `13306`：

```powershell
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

### 容器名 novahub-app 冲突

说明之前有同名容器残留。先停止当前编排，再重新启动：

```powershell
docker compose -f deploy/docker-compose.yml down
docker compose -f deploy/docker-compose.yml up -d --build
```

### 网页 500/502 或打不开

先看后端健康检查和容器日志：

```powershell
curl http://localhost:9080/actuator/health
docker logs --tail 200 novahub-app
docker logs --tail 100 novahub-nginx
```

`novahub-app` 健康后，再访问 http://localhost:8089/。如果健康检查仍未 `UP`，优先检查 MySQL、Redis、Kafka 是否健康。

### 中文乱码

数据库和初始化脚本都使用 `utf8mb4`。手动导入 SQL 时务必带上 `--default-character-set=utf8mb4`，PowerShell 读取文件时使用 `-Encoding UTF8`。

### Elasticsearch IK 分词告警

默认 Compose 使用官方 Elasticsearch 镜像，不安装 IK 插件。当前 mapping 使用内置 `standard` analyzer，保证默认环境稳定启动。如需增强中文分词，可自定义 ES 镜像安装 IK 后再替换 analyzer。

## 验证命令

```powershell
mvn -pl nova-web -am test
mvn -pl nova-web -am package -DskipTests
docker compose -f deploy/docker-compose.yml up -d --build
curl http://localhost:9080/actuator/health
curl http://localhost:8089/api/tags/hot
```

## 项目结构

```text
NovaHub/
├── db/sql/                 # 初始化 SQL 与演示数据
├── deploy/                 # Docker Compose、Nginx、压测脚本
├── docs/                   # 架构说明、压测报告、运维文档
├── nova-common/            # 通用响应、异常、JWT、Redis、限流、幂等
├── nova-user/              # 用户、登录、关注、权限
├── nova-content/           # 内容、标签、审核、上传
├── nova-interaction/       # 点赞、收藏、评论
├── nova-feed/              # Feed 流
├── nova-hotrank/           # 热榜统计
├── nova-search/            # Elasticsearch 搜索
├── nova-notify/            # 通知
├── nova-recommend/         # 推荐
├── nova-monitor/           # 监控指标
└── nova-web/               # Spring Boot 启动入口与静态前端
```

## 架构资料

- [系统架构说明](docs/architecture.md)
- [性能压测报告](docs/performance-report.md)
- [Feed 架构说明](nova-feed/docs/FEED_ARCHITECTURE.md)
- [推荐系统说明](nova-recommend/docs/RECOMMEND_ARCHITECTURE.md)

## 简历写法参考

可以这样描述项目：

> NovaHub 是一个基于 Spring Boot 3 + MyBatis-Plus + Redis + Kafka + Elasticsearch 的内容社区平台。我负责核心后端链路和 Docker 化演示环境，实现了用户登录、内容发布、点赞收藏评论、Feed、热榜、搜索索引同步和静态前端演示页；针对热点内容使用 Redis ZSet + Caffeine 本地缓存，写链路通过幂等锁、滑动窗口限流和 Kafka 事件解耦，提高了接口稳定性和可扩展性。
