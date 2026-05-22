-- ================================================
-- NovaHub 推荐系统扩展表
-- ================================================

-- -----------------------------------------------
-- A/B 测试实验表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `ab_experiment` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `experiment_id`    VARCHAR(64) NOT NULL COMMENT '实验ID',
  `name`             VARCHAR(255) NOT NULL COMMENT '实验名称',
  `description`      TEXT COMMENT '实验描述',
  `traffic`          DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT '流量占比 0.0001-1.0',
  `status`           TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0-未开始 1-运行中 2-已结束',
  `start_time`       DATETIME DEFAULT NULL COMMENT '开始时间',
  `end_time`         DATETIME DEFAULT NULL COMMENT '结束时间',
  `metrics`          JSON COMMENT '关注指标列表',
  `create_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exp_id` (`experiment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A/B测试实验表';

-- 预置示例实验
INSERT INTO `ab_experiment` (`experiment_id`, `name`, `description`, `traffic`, `status`, `metrics`) VALUES
  ('rec_cf_vs_cb_001', '协同过滤 vs 基于内容推荐', '对比协同过滤和基于内容推荐的推荐效果', 0.5000, 1, '["ctr", "like_rate", "dwell_time"]');

-- -----------------------------------------------
-- A/B 测试桶表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `ab_bucket` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `experiment_id`    VARCHAR(64) NOT NULL COMMENT '实验ID',
  `bucket_id`        VARCHAR(16) NOT NULL COMMENT '桶ID',
  `bucket_name`      VARCHAR(64) NOT NULL COMMENT '桶名称',
  `weight`           DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT '权重',
  `config`           JSON COMMENT '桶配置参数',
  `description`      VARCHAR(255) DEFAULT NULL COMMENT '桶描述',
  `create_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exp_bucket` (`experiment_id`, `bucket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A/B测试桶表';

-- 预置示例桶
INSERT INTO `ab_bucket` (`experiment_id`, `bucket_id`, `bucket_name`, `weight`, `config`, `description`) VALUES
  ('rec_cf_vs_cb_001', 'A', '协同过滤组', 0.5000, '{"cf_weight": 0.6, "cb_weight": 0.4, "hot_weight": 0}', '协同过滤权重更高'),
  ('rec_cf_vs_cb_001', 'B', '基于内容组', 0.5000, '{"cf_weight": 0.2, "cb_weight": 0.8, "hot_weight": 0}', '基于内容推荐权重更高');

-- -----------------------------------------------
-- 用户推荐行为记录表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `user_recommend_behavior` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `content_id`       BIGINT UNSIGNED NOT NULL COMMENT '内容ID',
  `behavior_type`    VARCHAR(32) NOT NULL COMMENT '行为类型:EXPOSURE/CLICK/LIKE',
  `recommend_way`    VARCHAR(32) DEFAULT NULL COMMENT '推荐方式:cf/cb/hybrid/hot',
  `experiment_id`    VARCHAR(64) DEFAULT NULL COMMENT '实验ID',
  `bucket_id`        VARCHAR(16) DEFAULT NULL COMMENT '桶ID',
  `position`         INT DEFAULT NULL COMMENT '展示位置',
  `create_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_content` (`user_id`, `content_id`),
  KEY `idx_user_behavior` (`user_id`, `behavior_type`),
  KEY `idx_experiment` (`experiment_id`, `bucket_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户推荐行为记录表';

-- ================================================
-- Redis Key 设计（供参考，实际使用见架构文档）
-- ================================================
-- 推荐协同过滤相关
-- KEYS recommend:cf:*
--
-- 用户行为向量
-- user:likes:{userId}                → Set<contentId>
-- content:likes:{contentId}          → Set<userId>
--
-- 协同过滤缓存
-- recommend:cf:similarity:{userId}  → Hash<similarUserId, similarity>
-- recommend:cf:result:{userId}      → ZSet<contentId, score>
--
-- 基于内容推荐相关
-- KEYS recommend:cb:*
--
-- 用户标签画像
-- recommend:cb:profile:{userId}      → Hash<tagId, weight>
--
-- 内容标签缓存
-- recommend:content:tags:{contentId} → Set<tagId>
-- recommend:tag:contents:{tagId}     → ZSet<contentId, publishTimestamp>
--
-- 推荐结果缓存
-- recommend:result:{userId}:{type}   → ZSet<contentId, score>
--
-- A/B测试相关
-- KEYS recommend:ab:*
--
-- 用户实验分组
-- recommend:ab:user:{userId}:{expId} → String<bucketId>
--
-- 去重过滤相关
-- KEYS user:*
--
-- 用户已读历史
-- user:read:history:{userId}         → Set<contentId>
--
-- 用户黑名单
-- user:blocklist:{userId}            → Set<userId>
