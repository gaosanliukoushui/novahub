# NovaHub 内容社区平台 - 项目概览

> 本文件是 NovaHub 项目的完整概览，每次新会话只需阅读此文件即可了解整个项目。

---

## 1. 项目基本信息

| 属性 | 值 |
|------|-----|
| 项目名称 | NovaHub |
| 项目定位 | 高并发内容社区后端系统 |
| 核心功能 | 内容发布、点赞评论、Feed流推荐、热榜统计、搜索与行为分析 |
| Java版本 | 17 |
| 构建工具 | Maven |
| 代码仓库 | Git |

---

## 2. 技术栈

| 层级 | 技术选型 |
|------|---------|
| 基础框架 | Java 17 + Spring Boot 3.2.5 |
| ORM | MyBatis-Plus 3.5.7 |
| 缓存 | Redis (Lettuce) |
| 消息队列 | Apache Kafka 3.1.4 |
| 搜索引擎 | Elasticsearch 8.13.4（内置 standard analyzer，可扩展 IK 分词） |
| 对象存储 | MinIO 8.5.7 |
| 任务调度 | XXL-Job 2.4.1 |
| 链路追踪 | SkyWalking APM 9.6.0 |
| API文档 | Knife4j 4.5.0 (OpenAPI3) |
| 认证 | JWT (jjwt 0.12.5) |
| 工具库 | Hutool 5.8.27, FastJSON2 2.0.47 |

---

## 3. 模块结构（11个Maven模块）

```
NovaHub/
├── pom.xml                      # 父POM，统一管理版本依赖
├── nova-common/                 # 公共模块（被所有模块依赖）
├── nova-user/                   # 用户服务模块
├── nova-content/                # 内容服务模块
├── nova-interaction/            # 互动服务模块（点赞/收藏/评论）
├── nova-hotrank/                # 热榜服务模块
├── nova-feed/                   # Feed流服务模块
├── nova-search/                 # 搜索服务模块
├── nova-monitor/                # 行为日志分析与数据监控
├── nova-notify/                 # 实时通知与WebSocket推送
├── nova-recommend/              # 推荐系统模块
├── nova-web/                    # Web入口模块（汇总所有Controller）
├── config/                      # 配置文件（Docker、Envoy、Nginx）
├── docker/                      # Docker相关文件
└── db/
    └── sql/
        └── 001_initial_schema.sql  # 数据库初始化脚本
```

### 模块依赖关系图

```
nova-web (Web入口，依赖所有业务模块)
    ├── nova-common (被所有模块依赖)
    ├── nova-user
    ├── nova-content
    ├── nova-interaction (依赖 content, hotrank)
    ├── nova-hotrank
    ├── nova-feed (依赖 content)
    ├── nova-search (依赖 content, user)
    ├── nova-monitor (依赖 content)
    ├── nova-notify (依赖 user, hotrank, content)
    └── nova-recommend (依赖 user, content, interaction, hotrank)
```

---

## 4. 数据库设计（17张核心表）

### 4.1 用户与权限模块

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `sys_user` | 用户表 | id, username, password, phone, email, nickname, avatar, bio, status, follow_count, fans_count, works_count |
| `sys_role` | 角色表 | id, code, name, description (预置: ROLE_ADMIN, ROLE_USER) |
| `sys_permission` | 权限表 | id, code, name, type(菜单/按钮/接口), path |
| `sys_user_role` | 用户-角色关联表 | user_id, role_id |
| `sys_role_permission` | 角色-权限关联表 | role_id, permission_id |
| `sns_follow` | 关注关系表 | user_id, follow_id |

### 4.2 内容模块

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `content` | 内容表 | id, user_id, type(帖子/视频), title, content, media_url, status, review_status, like_count, collect_count, comment_count, view_count |
| `content_tag` | 标签表 | id, name, color, use_count |
| `content_tag_rel` | 内容-标签关联表 | content_id, tag_id |

### 4.3 互动模块

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `content_like` | 点赞表 | user_id, content_id |
| `content_collect` | 收藏表 | user_id, content_id, folder_id |
| `collect_folder` | 收藏夹表 | user_id, name, is_default |
| `content_comment` | 评论表 | id, content_id, user_id, parent_id, root_id, content, like_count, reply_count |
| `content_view` | 浏览记录表 | content_id, user_id |

### 4.4 热榜与统计模块

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `content_stats` | 内容统计表 | content_id, like_count, collect_count, comment_count, view_count, heat_score |
| `hot_content_record` | 热榜历史记录 | content_id, rank_type, rank, heat_score, record_date |

### 4.5 通知模块

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `sys_notification` | 通知消息表 | from_user_id, to_user_id, type(LIKE/COMMENT/FOLLOW等), content, target_id, is_read |

---

## 5. API接口结构

### 5.1 认证模块 `/api/auth`
| 方法 | 路径 | 说明 | 是否鉴权 |
|------|------|------|---------|
| POST | `/api/auth/register` | 用户注册 | 否 |
| POST | `/api/auth/login` | 用户登录，返回JWT Token | 否 |
| POST | `/api/auth/logout` | 退出登录 | 是 |

### 5.2 用户模块 `/api/users`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/users/me` | 获取当前用户信息 |
| GET | `/api/users/{id}` | 获取指定用户详情 |
| PUT | `/api/users/me` | 更新当前用户信息 |
| POST | `/api/users/{id}/follow` | 关注用户 |
| DELETE | `/api/users/{id}/follow` | 取消关注 |
| GET | `/api/users/{id}/followers` | 获取粉丝列表 |
| GET | `/api/users/{id}/followings` | 获取关注列表 |

### 5.3 内容模块 `/api/contents`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/contents` | 发布内容（帖子/视频） |
| GET | `/api/contents/{id}` | 内容详情 |
| PUT | `/api/contents/{id}` | 更新内容 |
| DELETE | `/api/contents/{id}` | 删除内容（软删除） |
| GET | `/api/contents` | 内容列表（分页） |
| POST | `/api/contents/drafts` | 保存草稿 |
| GET | `/api/contents/drafts` | 草稿列表 |

### 5.4 互动模块
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/contents/{id}/like` | 点赞 |
| DELETE | `/api/contents/{id}/like` | 取消点赞 |
| GET | `/api/contents/{id}/likes` | 点赞用户列表 |
| POST | `/api/contents/{id}/collect` | 收藏 |
| DELETE | `/api/contents/{id}/collect` | 取消收藏 |
| POST | `/api/contents/{id}/comments` | 发表评论（一级评论） |
| POST | `/api/comments/{id}/replies` | 回复评论（二级回复，树形结构） |
| GET | `/api/contents/{id}/comments` | 评论列表（支持热评排序） |
| DELETE | `/api/comments/{id}` | 删除评论 |

### 5.5 Feed流模块 `/api/feed`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/feed` | 获取Feed流（默认推荐流） |
| GET | `/api/feed/following` | 关注流 |
| GET | `/api/feed/recommend` | 推荐流 |
| GET | `/api/feed/hot` | 热门流 |

### 5.6 热榜模块 `/api/hotrank`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/hotrank` | 热榜列表 |
| GET | `/api/hotrank/all` | 综合热榜 |
| GET | `/api/hotrank/posts` | 帖子热榜 |
| GET | `/api/hotrank/videos` | 视频热榜 |
| GET | `/api/hotrank/trending` | 趋势榜 |
| POST | `/api/hotrank/recalculate` | 手动触发热榜重算 |

### 5.7 搜索模块 `/api/search`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/search/content` | 内容全文搜索（支持高亮） |
| GET | `/api/search/users` | 用户搜索 |
| GET | `/api/search/tags` | 标签搜索 |
| GET | `/api/search/suggest` | 搜索建议（自动补全） |
| POST | `/api/search/rebuild` | 重建搜索索引 |

### 5.8 推荐模块 `/api/recommend`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/recommend` | 获取个性化推荐 |
| POST | `/api/recommend/exposure` | 记录推荐曝光 |
| POST | `/api/recommend/click` | 记录推荐点击 |
| POST | `/api/recommend/refresh` | 刷新推荐结果 |
| GET | `/api/recommend/hot` | 热门推荐 |

### 5.9 通知模块 `/api/notify`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/notify/list` | 通知列表 |
| GET | `/api/notify/unread-count` | 未读消息数 |
| POST | `/api/notify/read-all` | 全部标记已读 |
| POST | `/api/notify/read/{id}` | 单条标记已读 |

---

## 6. Redis Key设计

| Key Pattern | 类型 | 说明 |
|-------------|------|------|
| `user:token:{userId}` | String | 用户登录态 |
| `token:blacklist:{token}` | String | Token黑名单（登出时加入） |
| `user:likes:{userId}` | Set | 用户点赞内容集合 |
| `content:likes:{contentId}` | Set | 内容被赞用户集合 |
| `like:rank:content` | ZSet | 内容点赞排行榜（按点赞数排序） |
| `user:followings:{userId}` | Set | 用户关注集合 |
| `user:followers:{userId}` | Set | 用户粉丝集合 |
| `feed:inbox:{userId}` | ZSet | 用户收件箱（关注流，时间戳为score） |
| `feed:recommend:list` | ZSet | 推荐流内容池 |
| `hotrank:list:{type}` | ZSet | 热榜列表（all/post/video） |
| `hotrank:score:{contentId}` | String | 内容热度分数 |
| `content:detail:{contentId}` | Hash | 内容详情缓存 |
| `comment:hot:{contentId}` | ZSet | 热评排序（点赞数+时间权重） |

---

## 7. Kafka Topic设计

| Topic | 分区数 | 说明 |
|-------|--------|------|
| `content-publish` | 3 | 内容发布事件（触发异步审核） |
| `content-review` | 1 | 审核结果回调 |
| `content-stats` | 3 | 行为统计事件（点赞/评论/收藏/浏览） |
| `feed-push` | 3 | Feed推拉事件 |

---

## 8. Elasticsearch索引设计

| 索引名 | 说明 | 主要字段 |
|--------|------|---------|
| `nova_content` | 内容索引 | title/content/tagNames 使用 standard analyzer，keyword 子字段用于精确过滤，可按需扩展 IK |

---

## 9. 核心业务算法

### 9.1 热度计算算法

```java
// 基础版：静态权重
score = 点赞数×3 + 评论数×5 + 收藏数×4 + 浏览数×1

// 进阶版：引入时间衰减
newScore = (currentScore + delta) × decayFactor^hoursElapsed
decayFactor = 0.95  // 每小时衰减5%
```

### 9.2 大V用户限流策略

当用户粉丝数 > 10,000 时，仅向10%的粉丝推送内容（采样策略），避免推送风暴。

### 9.3 缓存一致性策略

采用 Cache Aside + 延迟双删模式：
1. 先删除缓存
2. 更新数据库
3. 延迟再次删除缓存（异步）

---

## 10. 公共模块核心组件（nova-common）

### 10.1 统一响应
| 类 | 路径 | 说明 |
|----|------|------|
| `Result<T>` | `common/result/Result.java` | 统一响应封装（code, message, data） |
| `ResultCode` | `common/result/ResultCode.java` | 响应状态码枚举 |
| `PageResult<T>` | `common/result/PageResult.java` | 分页响应封装（records, total, pages） |

### 10.2 全局异常处理
| 类 | 路径 | 说明 |
|----|------|------|
| `GlobalExceptionHandler` | `common/exception/GlobalExceptionHandler.java` | 全局异常捕获与响应包装 |

### 10.3 工具类
| 类 | 路径 | 说明 |
|----|------|------|
| `JwtUtils` | `common/utils/JwtUtils.java` | JWT生成与验证 |
| `SecurityUtils` | `common/utils/SecurityUtils.java` | 安全上下文（获取当前用户） |
| `RedisUtils` | `common/utils/RedisUtils.java` | Redis操作封装 |

### 10.4 自定义注解
| 注解 | 说明 |
|------|------|
| `@NoAuth` | 跳过权限校验 |
| `@NoLogin` | 跳过登录校验（允许匿名访问） |
| `@Idempotent` | 接口幂等性保证 |
| `@RateLimitBySlideWindow` | 滑动窗口限流 |

---

## 11. 关键配置文件

| 文件 | 说明 |
|------|------|
| `application.yml` | 主配置（端口8080，数据库/Redis/Kafka连接） |
| `application-dev.yml` | 开发环境配置 |
| `application-prod.yml` | 生产环境配置 |
| `docker-compose.yml` | 基础设施容器编排（MySQL/Redis/Kafka/ES/MinIO） |
| `nginx.conf` | Nginx反向代理与负载均衡配置 |

---

## 12. 项目状态

### 已完成

- **阶段一：核心业务闭环** ✅
  - 项目基础搭建（Spring Boot + MyBatis-Plus + Redis + JWT）
  - 用户系统（注册/登录/关注/RBAC）
  - 内容发布系统（帖子/视频/草稿/审核队列）
  - 点赞收藏系统（Redis Set + Lua脚本原子操作）
  - 评论系统（树形结构/热评排序/游标分页）

- **阶段二：技术深度** ✅
  - Feed流系统（关注流/推荐流/热门流/大V限流）
  - 热榜系统（Kafka实时消费/热度算法/多维度热榜）
  - 搜索系统（ES 全文搜索/标准分词/可扩展 IK/高亮/数据同步）

- **阶段三：工程化与高并发** ✅
  - 行为日志分析（Kafka采集/HyperLogLog统计/数据看板）
  - 推荐系统（协同过滤/内容推荐/A/B测试框架）
  - 实时通知（WebSocket/事件驱动推送）
  - Docker部署（Nginx/XXL-Job/SkyWalking）

### 待完成（技术债务）

- [ ] 接口限流（Redis + 滑动窗口/令牌桶）
- [ ] 接口幂等性设计
- [ ] 分布式Session方案
- [ ] 数据库读写分离（主从配置）
- [ ] 分库分表调研与设计
- [ ] 链路追踪（SkyWalking接入）
- [ ] 日志收集（ELK日志中心）
- [ ] 压测报告（JMeter/wrk压测）

---

## 13. 快速导航

| 需求 | 关键代码位置 |
|------|------------|
| 新增API | `nova-web/src/main/java/com/novahub/web/controller/` |
| 用户认证逻辑 | `nova-user/src/main/java/com/novahub/user/service/AuthService.java` |
| 内容发布逻辑 | `nova-content/src/main/java/com/novahub/content/service/ContentService.java` |
| 点赞/收藏逻辑 | `nova-interaction/src/main/java/com/novahub/interaction/service/` |
| 热榜计算逻辑 | `nova-hotrank/src/main/java/com/novahub/hotrank/service/` |
| Feed流逻辑 | `nova-feed/src/main/java/com/novahub/feed/service/` |
| 搜索逻辑 | `nova-search/src/main/java/com/novahub/search/service/` |
| 推荐算法 | `nova-recommend/src/main/java/com/novahub/recommend/service/` |
| 实时通知 | `nova-notify/src/main/java/com/novahub/notify/service/` |
| 数据库表结构 | `db/sql/001_initial_schema.sql` |
| 项目待办清单 | `todo_list.md` |

---

*最后更新：2026-05-21*
