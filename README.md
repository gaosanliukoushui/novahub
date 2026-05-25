# NovaHub

[![NovaHub CI](https://github.com/gaosanliukoushui/novahub/actions/workflows/ci.yml/badge.svg)](https://github.com/gaosanliukoushui/novahub/actions/workflows/ci.yml)

NovaHub 是一个面向简历展示和面试讲解的内容社区项目，覆盖用户注册登录、内容发布、草稿与审核、点赞收藏评论、Feed、热榜、搜索、推荐、通知、监控看板和管理演示页。项目重点不是“做一个普通 CRUD 网站”，而是把内容社区里常见的工程问题做出可演示的落地版本：缓存、限流、幂等、可靠事件、搜索索引同步、热榜计算、Docker 一键部署、自动验证和压测材料。

## 快速入口

- 深度手册：[docs/project-deep-dive.md](E:/我的项目/NovaHub/docs/project-deep-dive.md)
- 架构说明：[docs/architecture.md](E:/我的项目/NovaHub/docs/architecture.md)
- 压测报告：[docs/performance-report.md](E:/我的项目/NovaHub/docs/performance-report.md)
- 演示脚本：[docs/demo-script.md](E:/我的项目/NovaHub/docs/demo-script.md)
- 简历写法：[docs/resume-guide.md](E:/我的项目/NovaHub/docs/resume-guide.md)
- 项目总览：[PROJECT_OVERVIEW.md](E:/我的项目/NovaHub/PROJECT_OVERVIEW.md)

## 技术栈

- 后端：Java 17、Spring Boot 3.2.5、MyBatis-Plus、JWT、Knife4j
- 中间件：Redis、Kafka、Elasticsearch、MinIO
- 工程能力：Caffeine、Docker Compose、SkyWalking、Logstash、Kibana、Testcontainers、JMeter
- 前端：静态 HTML / CSS / JavaScript，无 Node 构建依赖

## 功能概览

- 用户与权限：注册、登录、个人资料、关注关系、RBAC
- 内容能力：发布、草稿、审核、标签、详情、列表、上传
- 互动能力：点赞、取消点赞、收藏、评论、回复、通知
- 分发能力：关注流、推荐流、热门流
- 搜索能力：全文搜索、标签搜索、索引重建
- 数据能力：热榜、PV/UV、推荐曝光点击、运营看板
- 管理能力：热榜预热、搜索重建、演示数据重载、内容审核

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

### 健康检查

```powershell
curl http://localhost:9080/actuator/health
```

返回 `UP` 后，再访问前端页面。

## 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端首页 | `http://localhost:8089/` | 内容流、详情、评论、点赞收藏、登录 |
| Dashboard | `http://localhost:8089/#/dashboard` | PV/UV、热榜、推荐数据 |
| Admin | `http://localhost:8089/#/admin` | 仅管理员可用 |
| API 文档 | `http://localhost:8089/doc.html` | Knife4j / OpenAPI |
| 后端健康检查 | `http://localhost:9080/actuator/health` | 返回 `UP` 代表后端正常 |
| MySQL | `localhost:13306` | `novahub / root123` |
| MinIO Console | `http://localhost:9001/` | `minioadmin / minioadmin` |
| Kafka UI | `http://localhost:8090/` | Topic 查看 |
| SkyWalking UI | `http://localhost:8088/` | 可选链路追踪 |

## 演示账号

| 用户名 | 密码 | 用途 |
|------|------|------|
| `demo_user` | `123456` | 普通用户演示 |
| `demo_admin` | `123456` | 管理员演示 |

## 推荐演示路径

1. 打开 `http://localhost:8089/`，确认首页内容流、热榜和标签正常显示。
2. 点击任意内容详情，查看评论区、点赞收藏和相关推荐。
3. 使用 `demo_user / 123456` 登录，演示发布、草稿、收藏和个人中心。
4. 打开 `#/dashboard`，展示 PV/UV、热榜和推荐数据。
5. 使用 `demo_admin / 123456` 登录后进入 `#/admin`，演示热榜预热、搜索重建、演示数据重载和内容审核。

## 演示数据

首次启动新 volume 时，会自动执行：

- [001_initial_schema.sql](E:/我的项目/NovaHub/db/sql/001_initial_schema.sql)
- [002_recommend_schema.sql](E:/我的项目/NovaHub/db/sql/002_recommend_schema.sql)
- [003_demo_data.sql](E:/我的项目/NovaHub/db/sql/003_demo_data.sql)
- [004_outbox_schema.sql](E:/我的项目/NovaHub/db/sql/004_outbox_schema.sql)

如果数据库已经初始化过，需要手动重载演示数据：

```powershell
docker exec novahub-mysql sh -c "mysql -unovahub -proot123 --default-character-set=utf8mb4 nova_hub < /docker-entrypoint-initdb.d/003_demo_data.sql"
docker exec novahub-mysql sh -c "mysql -unovahub -proot123 --default-character-set=utf8mb4 nova_hub < /docker-entrypoint-initdb.d/004_outbox_schema.sql"
```

## 验证命令

```powershell
mvn -pl nova-web -am test
mvn -pl nova-web -am package -DskipTests
docker compose -f deploy/docker-compose.yml up -d --build
curl http://localhost:9080/actuator/health
./scripts/smoke.ps1
```

## Windows 下 Testcontainers

如果 Docker 已启动，但 Maven 测试提示 `Could not find a valid Docker environment`，通常是 Testcontainers 没有正确识别 Docker Desktop 的 endpoint。可以在 Docker Desktop 中开启：

```text
Expose daemon on tcp://localhost:2375 without TLS
```

然后执行：

```powershell
$env:DOCKER_HOST="tcp://localhost:2375"
mvn -pl nova-web -Dtest=CoreFlowIntegrationTest test
```

也可以直接运行：

```powershell
./scripts/testcontainers.ps1
```

## 常见问题

### MySQL 端口冲突

项目已将 MySQL 映射为 `13306:3306`，避免占用本机 `3306`。如果仍冲突，请检查是否有其他容器占用 `13306`。

```powershell
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

### `novahub-app` 容器名冲突

```powershell
docker compose -f deploy/docker-compose.yml down
docker compose -f deploy/docker-compose.yml up -d --build
```

### 页面 500 / 502 或打不开

```powershell
curl http://localhost:9080/actuator/health
docker logs --tail 200 novahub-app
docker logs --tail 100 novahub-nginx
```

### 中文乱码

- SQL 导入时使用 `--default-character-set=utf8mb4`
- PowerShell 读取文件时使用 `-Encoding UTF8`
- 新文档统一保持 UTF-8 编码

### Elasticsearch IK 报错

默认 Docker 环境不安装 IK。项目当前使用 `standard` analyzer 保证索引可建、服务可启动；后续如需更强中文分词，再扩展自定义 ES 镜像。

## 项目结构

```text
NovaHub/
├── db/sql/                 # 初始化脚本、推荐表结构、演示数据、outbox
├── deploy/                 # Docker、Nginx、JMeter、wrk 配置
├── docs/                   # 深度手册、架构、压测、简历写法、演示材料
├── scripts/                # smoke、Testcontainers 等脚本
├── nova-common/            # 公共能力
├── nova-user/              # 用户与权限
├── nova-content/           # 内容与标签
├── nova-interaction/       # 点赞、收藏、评论
├── nova-feed/              # Feed 流
├── nova-hotrank/           # 热榜
├── nova-search/            # 搜索
├── nova-monitor/           # 看板与监控
├── nova-notify/            # 通知
├── nova-recommend/         # 推荐
└── nova-web/               # 启动入口与前端
```

## 简历里怎么写

一句话版：

> 基于 Spring Boot 3、MyBatis-Plus、Redis、Kafka、Elasticsearch、MySQL 构建内容社区系统，完成内容发布、互动、Feed、热榜、搜索、推荐、监控和 Docker 化演示闭环，并通过限流、幂等、Redis ZSet、Caffeine、本地快照回落和 outbox 可靠事件提升系统稳定性。

更完整的简历表述，直接参考：

- [docs/resume-guide.md](E:/我的项目/NovaHub/docs/resume-guide.md)

## 延伸阅读

- [docs/project-deep-dive.md](E:/我的项目/NovaHub/docs/project-deep-dive.md)
- [docs/architecture.md](E:/我的项目/NovaHub/docs/architecture.md)
- [docs/performance-report.md](E:/我的项目/NovaHub/docs/performance-report.md)
- [docs/demo-script.md](E:/我的项目/NovaHub/docs/demo-script.md)
