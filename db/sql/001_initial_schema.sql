-- ================================================
-- NovaHub 内容社区平台 - 数据库初始化脚本
-- 版本: v1.0
-- 描述: 阶段一核心表结构（14张表）
-- ================================================

SET NAMES utf8mb4;

-- -----------------------------------------------
-- 1. 用户表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`    VARCHAR(32)  NOT NULL COMMENT '用户名',
  `password`    VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `nickname`    VARCHAR(64)  NOT NULL COMMENT '昵称',
  `avatar`      VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
  `bio`         VARCHAR(255) DEFAULT NULL COMMENT '个人简介',
  `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
  `follow_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '关注数',
  `fans_count`  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '粉丝数',
  `works_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '作品数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- -----------------------------------------------
-- 2. 角色表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(32)  NOT NULL COMMENT '角色编码：ROLE_ADMIN/ROLE_USER',
  `name`        VARCHAR(64)  NOT NULL COMMENT '角色名称',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

INSERT INTO `sys_role` (`code`, `name`, `description`) VALUES
  ('ROLE_ADMIN', '管理员', '系统管理员，拥有全部权限'),
  ('ROLE_USER', '普通用户', '普通注册用户');

-- -----------------------------------------------
-- 3. 权限表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(64)  NOT NULL COMMENT '权限编码',
  `name`        VARCHAR(64)  NOT NULL COMMENT '权限名称',
  `type`        TINYINT NOT NULL COMMENT '类型：1-菜单 2-按钮 3-接口',
  `parent_id`   BIGINT UNSIGNED DEFAULT 0 COMMENT '父权限ID，0表示顶级',
  `path`        VARCHAR(255) DEFAULT NULL COMMENT '路由或接口路径',
  `icon`        VARCHAR(100) DEFAULT NULL COMMENT '图标',
  `sort_order`  INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

INSERT INTO `sys_permission` (`code`, `name`, `type`, `parent_id`, `path`, `sort_order`) VALUES
  ('user:read', '查看用户', 3, 0, '/api/users/**', 1),
  ('user:write', '管理用户', 3, 0, '/api/users/**', 2),
  ('content:read', '查看内容', 3, 0, '/api/contents/**', 1),
  ('content:write', '发布内容', 3, 0, '/api/contents/**', 2),
  ('content:audit', '审核内容', 3, 0, '/api/admin/content/**', 3),
  ('comment:read', '查看评论', 3, 0, '/api/comments/**', 1),
  ('comment:write', '发表评论', 3, 0, '/api/comments/**', 2);

-- -----------------------------------------------
-- 4. 用户-角色关联表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id`      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 为默认管理员账号预置角色（用户ID需在注册后手动关联或通过后门接口创建）
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- -----------------------------------------------
-- 5. 角色-权限关联表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `role_id`       BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  `permission_id` BIGINT UNSIGNED NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ROLE_ADMIN 拥有全部权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission`;

-- ROLE_USER 拥有基础读权限和写权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
  (2, 1), (2, 3), (2, 4), (2, 6), (2, 7);

-- -----------------------------------------------
-- 6. 关注表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `sns_follow` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '关注者ID',
  `follow_id`   BIGINT UNSIGNED NOT NULL COMMENT '被关注者ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow` (`user_id`, `follow_id`),
  KEY `idx_follow_id` (`follow_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注关系表';

-- -----------------------------------------------
-- 7. 内容表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `content` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内容ID',
  `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '作者ID',
  `type`          TINYINT NOT NULL COMMENT '类型：1-帖子 2-视频',
  `title`         VARCHAR(255) DEFAULT NULL COMMENT '标题',
  `content`       TEXT COMMENT '正文（帖子）或描述（视频）',
  `cover_url`     VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
  `media_url`     VARCHAR(500) DEFAULT NULL COMMENT '媒体URL（视频地址或图片JSON数组）',
  `media_type`    VARCHAR(20) DEFAULT NULL COMMENT '媒体类型：image/video',
  `status`        TINYINT NOT NULL DEFAULT 0 COMMENT '发布状态：0-草稿 1-待审核 2-已发布 3-已下架',
  `review_status` TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态：0-待审核 1-通过 2-拒绝',
  `review_remark` VARCHAR(255) DEFAULT NULL COMMENT '审核备注/拒绝原因',
  `like_count`   INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数',
  `collect_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '收藏数',
  `comment_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论数',
  `view_count`   INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览数',
  `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `publish_time`  DATETIME DEFAULT NULL COMMENT '发布时间（审核通过后写入）',
  `is_deleted`    TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type_status` (`type`, `status`),
  KEY `idx_publish_time` (`publish_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容表';

-- -----------------------------------------------
-- 8. 标签表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `content_tag` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(64) NOT NULL COMMENT '标签名',
  `color`       VARCHAR(20) DEFAULT NULL COMMENT '标签颜色（如#FF5733）',
  `use_count`   INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '被使用次数（热度）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容标签表';

-- 预置一些常用标签
INSERT INTO `content_tag` (`name`, `color`, `use_count`) VALUES
  ('搞笑', '#FF6B6B', 0),
  ('美食', '#FFA500', 0),
  ('旅游', '#4ECDC4', 0),
  ('科技', '#45B7D1', 0),
  ('游戏', '#96CEB4', 0),
  ('音乐', '#DDA0DD', 0),
  ('影视', '#F7DC6F', 0),
  ('运动', '#85C1E9', 0);

-- -----------------------------------------------
-- 9. 内容-标签关联表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `content_tag_rel` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `content_id` BIGINT UNSIGNED NOT NULL COMMENT '内容ID',
  `tag_id`     BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_tag` (`content_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容标签关联表';

-- -----------------------------------------------
-- 10. 点赞表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `content_like` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '点赞用户ID',
  `content_id` BIGINT UNSIGNED NOT NULL COMMENT '被点赞内容ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_content` (`user_id`, `content_id`),
  KEY `idx_content_id` (`content_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞表';

-- -----------------------------------------------
-- 11. 收藏表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `content_collect` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '收藏用户ID',
  `content_id`  BIGINT UNSIGNED NOT NULL COMMENT '被收藏内容ID',
  `folder_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '收藏夹ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_content` (`user_id`, `content_id`),
  KEY `idx_content_id` (`content_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏表';

-- -----------------------------------------------
-- 12. 收藏夹表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_folder` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  `name`       VARCHAR(64) NOT NULL COMMENT '收藏夹名称',
  `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认收藏夹：0-否 1-是',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏夹表';

-- -----------------------------------------------
-- 13. 评论表（支持树形自关联）
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `content_comment` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `content_id`  BIGINT UNSIGNED NOT NULL COMMENT '所属内容ID',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '评论者ID',
  `parent_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '父评论ID，NULL表示一级评论',
  `root_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '根评论ID，NULL表示一级评论，用于聚合查询',
  `content`     TEXT NOT NULL COMMENT '评论正文',
  `like_count`  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数',
  `reply_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '直接回复数',
  `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-已删除 1-正常',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_content_id` (`content_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_root_id` (`root_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_content_status_time` (`content_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- -----------------------------------------------
-- 14. 内容浏览记录表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `content_view` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `content_id` BIGINT UNSIGNED NOT NULL COMMENT '被浏览内容ID',
  `user_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '浏览用户ID，NULL表示游客',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_content_id` (`content_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容浏览记录表';

-- ================================================
-- 热榜系统扩展表
-- ================================================

-- -----------------------------------------------
-- 15. 内容统计表（热榜数据快照）
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `content_stats` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `content_id`       BIGINT UNSIGNED NOT NULL COMMENT '内容ID',
  `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '作者ID',
  `type`             TINYINT NOT NULL COMMENT '内容类型：1-帖子 2-视频',
  `like_count`       INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数',
  `collect_count`    INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '收藏数',
  `comment_count`    INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论数',
  `view_count`       INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览数',
  `heat_score`       DOUBLE NOT NULL DEFAULT 0 COMMENT '热度分',
  `last_update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `create_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_id` (`content_id`),
  KEY `idx_type_heat` (`type`, `heat_score` DESC),
  KEY `idx_heat_score` (`heat_score` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容统计表';

-- -----------------------------------------------
-- 16. 热榜历史记录表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `hot_content_record` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `content_id`   BIGINT UNSIGNED NOT NULL COMMENT '内容ID',
  `content_type` TINYINT NOT NULL COMMENT '内容类型：1-帖子 2-视频',
  `rank_type`    TINYINT NOT NULL COMMENT '榜单类型：0-综合 1-帖子 2-视频 3-趋势 4-日榜 5-周榜',
  `rank`         INT NOT NULL COMMENT '上榜排名',
  `heat_score`   DOUBLE NOT NULL DEFAULT 0 COMMENT '上榜时热度分',
  `record_date`  DATETIME NOT NULL COMMENT '记录时间',
  `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_record_date` (`record_date`),
  KEY `idx_content_type_rank` (`content_type`, `rank`),
  KEY `idx_rank_type` (`rank_type`, `rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热榜历史记录表';

-- ================================================
-- 通知系统扩展表
-- ================================================

-- -----------------------------------------------
-- 17. 通知消息表
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_notification` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `from_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '发送者ID',
  `to_user_id`   BIGINT UNSIGNED NOT NULL COMMENT '接收者ID',
  `type`         VARCHAR(32) NOT NULL COMMENT '通知类型：LIKE/COMMENT/FOLLOW/MENTION/SYSTEM',
  `content`      VARCHAR(500) DEFAULT NULL COMMENT '通知内容摘要',
  `target_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '目标ID（内容/用户/评论ID）',
  `target_type`  VARCHAR(32) DEFAULT NULL COMMENT '目标类型：CONTENT/USER/COMMENT',
  `is_read`      TINYINT NOT NULL DEFAULT 0 COMMENT '已读标记：0-未读 1-已读',
  `read_time`    DATETIME DEFAULT NULL COMMENT '阅读时间',
  `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted`   TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_to_user_id` (`to_user_id`),
  KEY `idx_from_user_id` (`from_user_id`),
  KEY `idx_type` (`type`),
  KEY `idx_is_read` (`is_read`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知消息表';
