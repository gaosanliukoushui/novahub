# 推荐系统架构设计文档

> NovaHub 内容社区平台 - 推荐系统模块
> 版本: v1.0
> 描述: 协同过滤 + 基于内容推荐 + A/B 测试框架

---

## 1. 系统架构

### 1.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         推荐系统架构                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────────┐    ┌────────────────┐   │
│  │  用户请求层  │───▶│   推荐服务层     │───▶│   数据层        │   │
│  │  Controller │    │   RecommendService│   │   MySQL/Redis  │   │
│  └─────────────┘    └────────┬─────────┘    └────────────────┘   │
│                              │                                   │
│         ┌────────────────────┼────────────────────┐             │
│         ▼                    ▼                    ▼             │
│  ┌─────────────┐    ┌─────────────────┐    ┌────────────────┐   │
│  │ 协同过滤引擎 │    │  基于内容引擎    │    │   A/B 测试框架  │   │
│  │Collaborative│    │  ContentBased   │    │   ABTestEngine  │   │
│  │ Filtering  │    │  Recommender    │    │                │   │
│  └─────────────┘    └─────────────────┘    └────────────────┘   │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    结果处理层                            │   │
│  │   去重过滤 → 混排融合 → 排序重排 → 缓存返回               │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Kafka 事件层                          │   │
│  │   用户行为事件 → 特征更新 → 实时推荐更新                  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 模块依赖关系

```
nova-recommend
    ├── nova-common      (公共工具、Redis、异常处理)
    ├── nova-user       (用户信息、关注关系)
    ├── nova-content    (内容信息、标签数据)
    ├── nova-interaction (点赞、收藏行为)
    └── nova-hotrank    (热度分计算)
```

---

## 2. 推荐算法设计

### 2.1 协同过滤 (Collaborative Filtering)

**原理**: "物以类聚，人以群分" — 找到与目标用户兴趣相似的用户，推荐他们喜欢的内容。

**实现方案**: 基于用户的协同过滤 (User-Based CF)

**核心步骤**:

1. **用户相似度计算** — 使用余弦相似度
   ```
   sim(u, v) = (L(u) ∩ L(v)) / sqrt(|L(u)| × |L(v)|)
   
   其中 L(u) 为用户 u 点赞过的内容集合
   ```

2. **候选用户选择** — 选取与目标用户相似度 Top-N 的用户

3. **推荐内容生成** — 从相似用户喜欢但目标用户未看过的内容中选取

**Redis 存储设计**:
```
# 用户行为向量 (用户点赞的内容集合)
user:behavior:vector:{userId}     → Set<contentId>

# 用户相似度缓存 (目标用户 → 相似用户列表)
user:similarity:{userId}         → Hash<similarUserId, similarity>

# 协同过滤推荐结果缓存
cf:recommend:{userId}            → ZSet<contentId, score>
```

**优缺点**:
- 优点: 能发现用户的潜在兴趣，不需要内容特征
- 缺点: 冷启动问题（新用户无行为数据时无法推荐）、稀疏性问题

### 2.2 基于内容的推荐 (Content-Based)

**原理**: "你喜欢这类内容，我给你推荐更多同类内容" — 根据内容的标签/分类进行匹配。

**实现方案**: 标签匹配 + 热度加权

**核心步骤**:

1. **用户画像构建** — 统计用户历史上喜欢的内容的标签分布
   ```
   tag_preference[tag] = 用户点赞该标签内容的次数 / 用户总点赞数
   ```

2. **内容匹配** — 计算候选内容与用户画像的匹配度
   ```
   match_score(content) = Σ(tag_weight × tag_preference[tag])
   ```

3. **热度融合** — 结合热度分进行排序
   ```
   final_score = match_score × α + heat_score × (1 - α)
   ```

**Redis 存储设计**:
```
# 用户标签偏好画像
user:tag:profile:{userId}        → Hash<tagId, weight>

# 标签下的内容列表
tag:contents:{tagId}             → ZSet<contentId, publishTimestamp>

# 内容-标签关联缓存
content:tags:{contentId}         → Set<tagId>
```

**优缺点**:
- 优点: 无冷启动问题、可解释性强
- 缺点: 推荐范围受限、难以发现新兴趣

### 2.3 混合推荐策略

采用加权混合方式:

```
final_score = cf_score × cf_weight + cb_score × cb_weight

其中:
- cf_weight + cb_weight = 1.0
- 冷启动用户: cf_weight = 0.2, cb_weight = 0.8
- 活跃用户:   cf_weight = 0.6, cb_weight = 0.4
```

---

## 3. 推荐结果去重与过滤

### 3.1 过滤规则

| 过滤类型 | 描述 | 实现方式 |
|---------|------|---------|
| 已读过滤 | 过滤用户已浏览/看过的内容 | Redis Set 存储已读内容ID |
| 已点赞过滤 | 过滤用户已点赞的内容（可选） | Redis Set `user:likes:{userId}` |
| 已收藏过滤 | 过滤用户已收藏的内容（可选） | Redis Set `user:collects:{userId}` |
| 作者黑名单 | 过滤用户拉黑的作者 | Redis Set `user:blocklist:{userId}` |
| 内容下架 | 过滤已删除/下架的内容 | MySQL 查询时过滤 status |
| 时间过滤 | 过滤过旧的内容（可配置） | 时间戳比较 |

### 3.2 去重策略

```
同一推荐结果中，同一作者的内容最多出现 N 次（默认 2 次）
```

### 3.3 Redis 去重缓存

```
# 用户已读内容记录
user:read:history:{userId}      → ZSet<contentId, readTimestamp>

# 去重结果缓存（每日重置）
user:recommend:filtered:{userId} → Set<contentId>
```

---

## 4. A/B 测试框架设计

### 4.1 A/B 测试流程

```
┌──────────┐    ┌─────────────┐    ┌──────────────┐    ┌─────────────┐
│ 用户请求  │───▶│ 实验分流    │───▶│ 策略执行     │───▶│ 结果记录    │
│          │    │ (Hash分流)   │    │ (不同策略)   │    │ (事件上报)  │
└──────────┘    └─────────────┘    └──────────────┘    └─────────────┘
```

### 4.2 实验配置

```json
{
  "experimentId": "rec_cf_vs_cb_001",
  "name": "协同过滤 vs 基于内容推荐对比实验",
  "traffic": 0.5,
  "buckets": [
    {"bucketId": "A", "weight": 0.5, "strategy": "cf_weight=0.6,cb_weight=0.4"},
    {"bucketId": "B", "weight": 0.5, "strategy": "cf_weight=0.2,cb_weight=0.8"}
  ],
  "metrics": ["ctr", "like_rate", "share_rate", "dwell_time"]
}
```

### 4.3 分流算法

使用 MurmurHash3 进行确定性分流:

```
bucket = murmurhash3(userId + experimentId) % 100
if (bucket < traffic * 100):
    # 实验组
else:
    # 对照组
```

### 4.4 核心指标

| 指标 | 计算方式 | 说明 |
|-----|---------|------|
| CTR | 点击数 / 曝光数 | 推荐内容点击率 |
| Like Rate | 点赞数 / 曝光数 | 点赞转化率 |
| 曝光数 | 推荐接口返回内容被浏览的次数 | 通过事件上报 |
| 点击数 | 用户点击内容的次数 | 通过事件上报 |

---

## 5. Redis Key 设计

### 5.1 Key 命名规范

```
推荐相关 Key 统一前缀: recommend:{module}:{submodule}:{identifier}

示例:
- recommend:cf:vector:1001        → 用户 1001 的协同过滤向量
- recommend:cf:similarity:1001   → 用户 1001 的相似用户列表
- recommend:cb:profile:1001      → 用户 1001 的内容偏好画像
- recommend:result:1001           → 用户 1001 的推荐结果缓存
- recommend:ab:exp:user1001      → 用户 1001 的实验分组
```

### 5.2 Key 过期策略

| Key 类型 | TTL | 说明 |
|---------|-----|------|
| 用户行为向量 | 30天 | 用户长期兴趣 |
| 相似用户缓存 | 1天 | 每日更新 |
| 标签偏好画像 | 7天 | 用户短期兴趣 |
| 推荐结果缓存 | 10分钟 | 热点内容可复用 |
| A/B 测试分组 | 永久 | 分组不变化 |

---

## 6. Kafka 事件设计

### 6.1 Topic 配置

| Topic | 用途 | Partition Key |
|-------|------|--------------|
| recommend-behavior | 用户行为事件 | userId |
| recommend-result | 推荐结果曝光事件 | userId |

### 6.2 事件格式

```json
{
  "eventType": "VIEW_RECOMMEND",
  "userId": 1001,
  "requestId": "uuid-xxx",
  "experimentId": "rec_cf_vs_cb_001",
  "bucketId": "A",
  "contentId": 2001,
  "position": 1,
  "recommendType": "cf",
  "timestamp": 1716187200000
}
```

---

## 7. API 接口设计

### 7.1 获取推荐列表

```
GET /api/recommend

Query Parameters:
- userId:     Long    (可选，登录用户)
- pageSize:   Integer (默认 20)
- pageNum:    Integer (默认 1)
- feedType:   String  (cf/cb/hybrid, 默认 hybrid)

Response:
{
  "code": 200,
  "data": {
    "list": [
      {
        "contentId": 2001,
        "title": "...",
        "author": {...},
        "recommendReason": "你关注的人都在看",
        "score": 0.95,
        "position": 1
      }
    ],
    "experiment": {
      "expId": "rec_cf_vs_cb_001",
      "bucketId": "A"
    }
  }
}
```

---

## 8. 性能优化策略

### 8.1 缓存策略

1. **推荐结果缓存**: 热门用户推荐结果缓存 10 分钟
2. **用户画像缓存**: 标签偏好画像缓存 1 小时
3. **相似用户缓存**: 每日批量计算，缓存 24 小时

### 8.2 异步处理

1. 协同过滤相似度计算异步进行
2. 用户画像更新通过 Kafka 异步处理
3. 曝光事件异步上报，不阻塞推荐请求

### 8.3 降级策略

| 场景 | 降级方案 |
|-----|---------|
| Redis 不可用 | 仅使用基于内容推荐 |
| 新用户（无行为数据） | 100% 基于内容推荐 |
| 相似用户为空 | fallback 到热门内容 |

---

## 9. 数据库表设计

### 9.1 用户行为记录表 (可选存储)

```sql
CREATE TABLE IF NOT EXISTS `user_recommend_behavior` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`          BIGINT UNSIGNED NOT NULL,
  `content_id`       BIGINT UNSIGNED NOT NULL,
  `behavior_type`    VARCHAR(32) NOT NULL COMMENT 'LIKE/COLLECT/VIEW',
  `recommend_way`    VARCHAR(32) DEFAULT NULL COMMENT 'cf/cb/hybrid',
  `experiment_id`    VARCHAR(64) DEFAULT NULL,
  `bucket_id`        VARCHAR(16) DEFAULT NULL,
  `position`         INT DEFAULT NULL,
  `create_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_content` (`user_id`, `content_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 9.2 A/B 实验配置表

```sql
CREATE TABLE IF NOT EXISTS `ab_experiment` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `experiment_id`   VARCHAR(64) NOT NULL COMMENT '实验ID',
  `name`             VARCHAR(255) NOT NULL COMMENT '实验名称',
  `description`      TEXT,
  `traffic`          DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT '流量占比 0.0001-1.0',
  `status`           TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0-未开始 1-运行中 2-已结束',
  `start_time`       DATETIME DEFAULT NULL,
  `end_time`         DATETIME DEFAULT NULL,
  `metrics`          JSON COMMENT '关注指标列表',
  `create_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exp_id` (`experiment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ab_bucket` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `experiment_id`   VARCHAR(64) NOT NULL COMMENT '实验ID',
  `bucket_id`        VARCHAR(16) NOT NULL COMMENT '桶ID',
  `bucket_name`      VARCHAR(64) NOT NULL COMMENT '桶名称',
  `weight`           DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT '权重',
  `config`           JSON COMMENT '桶配置参数',
  `description`      VARCHAR(255) DEFAULT NULL,
  `create_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exp_bucket` (`experiment_id`, `bucket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 10. 扩展计划

### 10.1 短期 (v1.0)

- [x] 协同过滤推荐引擎
- [x] 基于内容推荐引擎
- [x] A/B 测试框架
- [x] 去重过滤机制

### 10.2 中期 (v2.0)

- [ ] 实时特征更新
- [ ] 深度学习排序模型 (DIN/DIEN)
- [ ] 多目标排序 (CTR + 停留时长)

### 10.3 长期 (v3.0)

- [ ] 图神经网络推荐
- [ ] 跨平台推荐
- [ ] 推荐多样性调控

---

## 附录: 算法公式汇总

### 协同过滤 - 余弦相似度
```
sim(u,v) = (|L(u) ∩ L(v)|) / sqrt(|L(u)| × |L(v)|)
```

### 基于内容 - 标签匹配度
```
score_cb = Σ(content_tag[t] × user_profile[t])
```

### 最终推荐分
```
score_final = w_cf × score_cf + w_cb × score_cb + w_heat × score_heat
```

### 热度衰减 (时间加权)
```
decay(t) = 1 / (1 + α × t)
其中 t 为内容发布至今的小时数，α 为衰减系数 (默认 0.05)
```
