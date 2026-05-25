-- NovaHub reliable event outbox migration.
-- Idempotent: safe to run repeatedly against existing demo databases.

CREATE TABLE IF NOT EXISTS `event_outbox` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_type`      VARCHAR(64) NOT NULL COMMENT '事件类型',
  `aggregate_type`  VARCHAR(64) NOT NULL COMMENT '聚合类型',
  `aggregate_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '业务聚合ID',
  `topic`           VARCHAR(128) DEFAULT NULL COMMENT '目标Topic',
  `event_key`       VARCHAR(128) DEFAULT NULL COMMENT '消息Key',
  `payload`         JSON NOT NULL COMMENT '事件载荷',
  `status`          TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待投递 1-已投递 2-失败',
  `retry_count`     INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_time` DATETIME DEFAULT NULL COMMENT '下次重试时间',
  `error_message`   VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_retry` (`status`, `next_retry_time`),
  KEY `idx_aggregate` (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可靠事件 outbox 表';
