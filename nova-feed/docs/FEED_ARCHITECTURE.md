# Feed 流系统架构设计文档

> NovaHub 内容社区平台 - Feed 流模块
> 版本: v1.0
> 描述: 推模式 (Write Diffusion) + Redis ZSet 实现关注流、推荐流、热门流

---

## 1. 三种 Feed 流模式对比

### 1.1 模式对比表

| 特性 | 推模式 (Write Diffusion) | 拉模式 (Read Fanout) | 混合模式 |
|------|------------------------|---------------------|---------|
| **写入时机** | 内容发布时写入粉丝收件箱 | 阅读时聚合粉丝内容 | 分级处理 |
| **读取复杂度** | O(1) 直接读收件箱 | O(n) 扫描所有关注 | 中等 |
| **存储成本** | 高（写放大） | 低 | 中等 |
| **延迟** | 低（读取快） | 高（读取慢） | 中等 |
| **大V问题** | 严重（写扩散爆炸） | 无（按需拉取） | 部分缓解 |
| **适用场景** | 中小 V、粉丝数 < 1万 | 超大 V (> 10万粉丝) | 大型平台 |

### 1.2 NovaHub 采用的方案

**NovaHub 采用推模式（Write Diffusion）+ 大V限流策略**，原因：

1. **用户体验优先**：读取延迟低，用户滑动 Feed 无感知
2. **粉丝规模可控**：社区平台粉丝数大多在 1,000 ~ 10,000 范围
3. **大V已有应对**：粉丝数 > 10,000 时自动切换采样策略
4. **实现简单**：Redis ZSet 天然支持时间序+分数序

---

## 2. 系统架构

### 2.1 整体数据流

```
┌─────────────────────────────────────────────────────────────────────┐
│                          内容发布流程                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  用户发布内容                                                         │
│       │                                                               │
│       ▼                                                               │
│  ContentController.publish()                                          │
│       │                                                               │
│       ├──▶ 写入 MySQL content 表                                      │
│       │                                                               │
│       └──▶ FeedEventProducer.sendPublishEvent()                       │
│                     │                                                 │
│                     ▼                                                 │
│              Kafka Topic: feed-push                                   │
│                     │                                                 │
│                     ▼                                                 │
│           FeedEventConsumer 消费事件                                   │
│                     │                                                 │
│                     ▼                                                 │
│          FeedService.pushToFollowers()                                │
│                     │                                                 │
│                     ▼                                                 │
│     ┌────────────────┴─────────────────┐                              │
│     │  判断粉丝数量是否超过大V阈值        │                              │
│     │  (BIG_V_THRESHOLD = 10,000)      │                              │
│     └─────────────┬───────────────────┘                              │
│       否            │ 是                                              │
│       ▼            ▼                                                 │
│  全量写入收件箱    采样写入 (10% 粉丝)                                  │
│       │            │                                                 │
│       └────────────┴─────────────────────────────────┐              │
│                    ▼                                   │              │
│           Redis ZSet                                   │              │
│     feed:inbox:{followerId}                           │              │
│     score = -publishTimestamp (时间倒序)              │              │
│     member = contentId:publishTimestamp               │              │
│                                                              │       │
└──────────────────────────────────────────────────────────────┘       │

┌─────────────────────────────────────────────────────────────────────┐
│                          内容读取流程                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  用户请求 Feed 流                                                     │
│       │                                                               │
│       ▼                                                               │
│  FeedController.getFeed(type)                                         │
│       │                                                               │
│       ├── type=1 关注流 ────▶ FeedService.getFollowingFeed()         │
│       │                        │                                     │
│       │                        ▼                                     │
│       │               读取 feed:inbox:{userId}                       │
│       │                        │                                     │
│       │                        ▼                                     │
│       ├── type=2 推荐流 ────▶ FeedService.getRecommendFeed()         │
│       │                        │                                     │
│       │                        ▼                                     │
│       │               读取 feed:recommend:list (全局热门)            │
│       │                        │                                     │
│       │                        ▼                                     │
│       └── type=3 热门流 ────▶ FeedService.getHotFeed()             │
│                                  │                                   │
│                                  ▼                                   │
│                         读取 hotrank:list:all (热榜)                │
│                                  │                                   │
│                                  ▼                                   │
│                         ContentMapper.batchSelect()                  │
│                                  │                                   │
│                                  ▼                                   │
│                         返回 FeedItemVO 列表                         │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.2 模块依赖关系

```
nova-feed
    ├── nova-common      (RedisUtils, SecurityUtils, Result)
    ├── nova-content    (Content 实体, ContentMapper, UserClient)
    └── Spring Kafka    (消息队列)
```

---

## 3. 核心实现细节

### 3.1 推模式写入 (pushToFollowers)

**核心逻辑**:

```java
public void pushToFollowers(Long userId, Long contentId,
                            Integer contentType, LocalDateTime publishTime) {
    // 1. 获取所有粉丝
    Set<Long> followerIds = getFollowerIds(userId);

    // 2. 大V限流：粉丝数 > 10000 时采样 10%
    if (fanCount > BIG_V_THRESHOLD) {
        int sampleCount = (int) Math.min(MAX_INBOX_SIZE, fanCount * 0.1);
        followerIds = randomSample(followerIds, sampleCount);
    }

    // 3. 构建 scoreKey: "contentId:timestamp"
    long timestamp = publishTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
    String scoreKey = contentId + ":" + timestamp;

    // 4. 写入每个粉丝的收件箱
    for (Long followerId : followerIds) {
        String inboxKey = String.format("feed:inbox:%d", followerId);
        redisUtils.zAdd(inboxKey, scoreKey, -timestamp); // 负数实现倒序
    }
}
```

**Redis 数据结构**:

```
Key:   feed:inbox:{userId}              → ZSet
Score: -publishTimestamp (时间戳取负，实现新内容在前)
Member: contentId:publishTimestamp       (格式: "12345:1716187200000")
```

**为什么用 ZSet 而不是 List**:
- ZSet 支持按 Score 范围查询 → 实现滚动分页
- ZSet 支持去重 → 同一内容不会重复出现
- ZSet 支持删除 → 内容删除时可精准移除

### 3.2 关注流读取 (getFollowingFeed)

**核心逻辑**:

```java
public List<FeedItemVO> getFollowingFeed(FeedRequest request) {
    Long userId = SecurityUtils.requireUserId();
    String inboxKey = String.format("feed:inbox:%d", userId);

    // Cursor 分页：上一页最后一条的时间戳作为游标
    double maxScore = request.getCursor() != null
            ? -request.getCursor()
            : Double.MAX_VALUE;

    // 反向范围查询（时间从新到旧）
    Set<String> items = redisUtils.zReverseRangeByScore(
            inboxKey, -maxScore, Double.MAX_VALUE);

    // 解析 contentId
    List<Long> contentIds = parseContentIds(items);

    // 批量加载内容详情
    List<Content> contents = loadContents(contentIds);

    // 转换为 VO
    return contents.stream()
            .map(c -> buildFeedItem(c, FeedType.FOLLOWING))
            .collect(Collectors.toList());
}
```

**分页策略 — 为什么用 Cursor 而不是 Offset**:

| 方式 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| Offset | `ZREVRANGE key 0 19` | 实现简单 | 深度分页性能差 |
| Cursor | `ZREVRANGEBYSCORE key -maxtime +inf` | 无论翻多少页性能稳定 | 无法跳页 |

Feed 流通常是无限滚动场景，Cursor 分页是业界标准方案。

### 3.3 推荐流 (getRecommendFeed)

**核心逻辑**:

```java
public List<FeedItemVO> getRecommendFeed(FeedRequest request) {
    // 从全局推荐列表读取
    Set<String> contentIds = redisUtils.zReverseRange(
            "feed:recommend:list", 0, RECOMMEND_FEED_SIZE - 1);

    List<Content> contents = loadContents(contentIds);

    return contents.stream()
            .limit(request.getPageSize())
            .map(c -> buildFeedItem(c, FeedType.RECOMMEND))
            .collect(Collectors.toList());
}
```

**推荐列表构建** (`buildRecommendFeed` 定时任务):

```
推荐分 = likeCount (点赞数)
排序规则：按点赞数降序，取 Top 200 写入 feed:recommend:list
```

> 注：当前为基于热度的简单推荐，未来可与 `nova-recommend` 模块协同实现协同过滤/基于内容推荐的个性化推荐。

### 3.4 热门流 (getHotFeed)

**核心逻辑**:

```java
public List<FeedItemVO> getHotFeed(FeedRequest request) {
    // 直接读取热榜模块的 Redis ZSet
    Set<String> hotContentIds = redisUtils.zReverseRange(
            "hotrank:list:all", 0, request.getPageSize() + 10);

    List<Content> contents = loadContents(hotContentIds);

    return contents.stream()
            .limit(request.getPageSize())
            .map(c -> buildFeedItem(c, FeedType.HOT))
            .collect(Collectors.toList());
}
```

**数据来源**: `nova-hotrank` 模块维护的实时热榜 ZSet。

### 3.5 大V限流策略

**问题**: 当用户粉丝数达到百万级别时，写入操作会占用大量 Redis 资源。

**NovaHub 策略**:

```java
if (fanCount > BIG_V_THRESHOLD) {  // 10,000 粉丝
    // 采样 10% 粉丝写入收件箱
    int sampleCount = (int) Math.min(MAX_INBOX_SIZE, fanCount * 0.1);
    followerIds = randomSample(followerIds, sampleCount);
}
```

| 粉丝数 | 策略 | 实际写入数 |
|-------|------|-----------|
| < 10,000 | 全量写入 | 全部粉丝 |
| 10,000 ~ 100,000 | 采样 10% | 1,000 ~ 10,000 |
| > 100,000 | 上限 1,000 | 1,000 |

**效果**:
- 大V粉丝看到大V内容的概率降低（采样策略）
- 降低 Redis 写入压力
- 普通用户不受影响

---

## 4. Kafka 事件设计

### 4.1 Topic 配置

| Topic | 分区数 | 用途 | Key |
|-------|--------|------|-----|
| `feed-push` | 3 | 发布/删除事件 | contentId |

### 4.2 事件格式

```json
{
  "contentId": 12345,
  "userId": 1001,
  "contentType": 1,
  "eventType": "PUBLISH",
  "eventTime": "2026-05-19T09:30:00"
}
```

| eventType | 说明 |
|-----------|------|
| PUBLISH | 内容发布 → 写入粉丝收件箱 |
| DELETE | 内容删除 → 从粉丝收件箱移除 |

---

## 5. Redis Key 设计

### 5.1 Key 命名规范

```
Feed 相关 Key 统一前缀: feed:{submodule}:{identifier}

Key                              类型   说明
─────────────────────────────────────────────────────────────
feed:inbox:{userId}              ZSet   用户收件箱 (关注流)
feed:recommend:list              ZSet   全局推荐列表 (Top 200 by likeCount)
feed:recommend:cache:{userId}    String 推荐流缓存 (10min TTL)
feed:hot:cache:{userId}          String 热门流缓存 (10min TTL)
user:followers:{userId}          Set    用户粉丝集合 (读取源)
```

### 5.2 TTL 与清理策略

| Key | TTL | 清理策略 |
|-----|-----|---------|
| `feed:inbox:{userId}` | 无 | ZSet 容量上限 1000，超出时异步裁剪旧数据 |
| `feed:recommend:list` | 无 | 定时任务 `buildRecommendFeed()` 每5分钟重建 |
| `feed:recommend:cache:{userId}` | 10分钟 | LRU 自然淘汰 |
| `feed:hot:cache:{userId}` | 10分钟 | LRU 自然淘汰 |

---

## 6. API 接口设计

### 6.1 获取 Feed 流

```
GET /api/feed

Query Parameters:
- type:       Integer  (1-关注流 2-推荐流 3-热门流, 默认 1)
- cursor:     Long     (上一页最后一条的时间戳，用于游标分页)
- lastId:     Long     (上一页最后一条的ID，可选)
- pageSize:   Integer  (默认 20，最大 50)
- contentType: Integer (1-帖子 2-视频，null表示全部)

Response:
{
  "code": 200,
  "data": [
    {
      "contentId": 12345,
      "userId": 1001,
      "authorNickname": "小明",
      "authorAvatar": "https://...",
      "contentType": 1,
      "title": "我的第一条帖子",
      "summary": "...",
      "coverUrl": "https://...",
      "likeCount": 128,
      "commentCount": 32,
      "viewCount": 2048,
      "publishTimestamp": 1716187200000,
      "feedType": 1,
      "isLiked": false,
      "isCollected": false
    }
  ]
}
```

### 6.2 其他接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/feed/following` | GET | 获取关注流 |
| `/api/feed/recommend` | GET | 获取推荐流 |
| `/api/feed/hot` | GET | 获取热门流 |
| `/api/feed/recommend/refresh` | POST | 手动触发推荐流重建 |

---

## 7. 性能优化策略

### 7.1 缓存策略

```
┌─────────────────────────────────────┐
│           缓存分层架构                │
├─────────────────────────────────────┤
│                                     │
│  L1: 本地缓存 (Caffeine)             │  ← 未使用，待扩展
│      TTL: 1分钟                      │
│      最大条目: 1000                  │
│                                     │
│  L2: Redis 缓存                      │
│      feed:recommend:cache:{userId}   │
│      TTL: 10分钟                     │
│                                     │
│  L3: Redis ZSet (数据源)             │
│      feed:inbox:{userId}            │
│      feed:recommend:list            │
│      无 TTL，主动管理                 │
│                                     │
└─────────────────────────────────────┘
```

### 7.2 异步处理

| 操作 | 方式 | 理由 |
|------|------|------|
| Feed 推送写入 | Kafka 异步 | 解耦内容发布与 Feed 写入，降低发布延迟 |
| 推荐流重建 | `@Async` 定时任务 | 批量操作不阻塞主流程 |
| 内容批量加载 | 异步预加载 | 用户滑动时提前加载下一页 |

### 7.3 收件箱容量控制

```java
private static final int MAX_INBOX_SIZE = 1000;

// 定时裁剪（可选实现）
@Scheduled(fixedRate = 3600000) // 每小时
public void trimInboxSize() {
    // ZREMRANGEBYRANK inbox 0 -(MAX_INBOX_SIZE+1)
    // 保留最新 1000 条
}
```

---

## 8. 降级策略

| 场景 | 降级方案 |
|------|---------|
| Redis 不可用 | 返回空列表或 fallback 到 MySQL 查询 |
| 关注流为空 | 自动 fallback 显示推荐流 |
| 推荐流为空 | fallback 到热门流 |
| Kafka 消费延迟 | 内容先入库，Feed 推送异步补偿 |

---

## 9. 扩展计划

### 9.1 短期优化

- [ ] 引入 Caffeine 本地缓存作为 L1 层
- [ ] 实现收件箱容量自动裁剪
- [ ] 支持关注流 + 推荐流混合展示

### 9.2 中期增强

- [ ] 与 `nova-recommend` 模块联动，实现个性化推荐
- [ ] 支持动态切换推/拉模式（按用户粉丝数自动选择）
- [ ] 大V内容单独标记和展示

### 9.3 长期演进

- [ ] 图数据库存储关注关系，优化好友推荐
- [ ] ML 排序模型重排 Feed（点击率预估）
- [ ] 跨平台 Feed 同步

---

## 附录：核心参数汇总

```java
// FeedService.java 中的核心常量
FEED_INBOX_KEY = "feed:inbox:%d"              // 用户收件箱 Key 模板
FEED_RECOMMEND_KEY = "feed:recommend:list"  // 全局推荐列表 Key
MAX_INBOX_SIZE = 1000                        // 收件箱最大容量
RECOMMEND_FEED_SIZE = 200                    // 推荐列表条目数
BIG_V_THRESHOLD = 10000                      // 大V粉丝数阈值
```
