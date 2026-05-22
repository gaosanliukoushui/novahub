-- ================================================
-- NovaHub 演示数据
-- 说明:
-- 1. 幂等脚本，可重复导入。
-- 2. 演示账号: demo_user / 123456, demo_admin / 123456。
-- 3. 密码为 BCrypt 哈希，避免在数据库中保存明文。
-- ================================================

SET NAMES utf8mb4;
USE nova_hub;

SET @demo_password = '$2a$10$1I.gZdNEnbAknpKQFLkqR.GQsMOkdf4rCthhSMTiPGHvKrArQQl6y';

INSERT INTO `sys_user`
  (`id`, `username`, `password`, `phone`, `email`, `nickname`, `avatar`, `bio`, `status`, `follow_count`, `fans_count`, `works_count`, `is_deleted`)
VALUES
  (9001, 'demo_user', @demo_password, NULL, 'demo_user@novahub.local', '演示用户', NULL, '用于面试演示的普通用户账号，覆盖发布、点赞、收藏和评论链路。', 1, 2, 2, 2, 0),
  (9002, 'demo_admin', @demo_password, NULL, 'demo_admin@novahub.local', '演示管理员', NULL, '用于演示管理视角和接口权限的账号。', 1, 1, 1, 1, 0),
  (9003, 'creator_ops', @demo_password, NULL, 'creator_ops@novahub.local', '产品观察员', NULL, '记录社区增长、内容分发和产品设计思考。', 1, 1, 2, 2, 0),
  (9004, 'creator_dev', @demo_password, NULL, 'creator_dev@novahub.local', '工程札记', NULL, '分享后端架构、缓存一致性和搜索链路实践。', 1, 1, 2, 2, 0)
ON DUPLICATE KEY UPDATE
  `password` = VALUES(`password`),
  `nickname` = VALUES(`nickname`),
  `bio` = VALUES(`bio`),
  `status` = VALUES(`status`),
  `follow_count` = VALUES(`follow_count`),
  `fans_count` = VALUES(`fans_count`),
  `works_count` = VALUES(`works_count`),
  `is_deleted` = 0;

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT 9001, `id` FROM `sys_role` WHERE `code` = 'ROLE_USER'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT 9002, `id` FROM `sys_role` WHERE `code` = 'ROLE_ADMIN'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT 9003, `id` FROM `sys_role` WHERE `code` = 'ROLE_USER'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT 9004, `id` FROM `sys_role` WHERE `code` = 'ROLE_USER'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sns_follow` (`id`, `user_id`, `follow_id`)
VALUES
  (90101, 9001, 9003),
  (90102, 9001, 9004),
  (90103, 9003, 9001),
  (90104, 9004, 9001)
ON DUPLICATE KEY UPDATE `create_time` = `create_time`;

INSERT INTO `collect_folder` (`id`, `user_id`, `name`, `is_default`)
VALUES
  (90201, 9001, '默认收藏夹', 1),
  (90202, 9002, '默认收藏夹', 1),
  (90203, 9003, '默认收藏夹', 1),
  (90204, 9004, '默认收藏夹', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `is_default` = VALUES(`is_default`);

INSERT INTO `content`
  (`id`, `user_id`, `type`, `title`, `content`, `cover_url`, `media_url`, `media_type`, `status`, `review_status`, `review_remark`,
   `like_count`, `collect_count`, `comment_count`, `view_count`, `create_time`, `update_time`, `publish_time`, `is_deleted`)
VALUES
  (90001, 9004, 1, '从 500 到可演示：NovaHub Docker 启动排障记录',
   '这篇笔记复盘 NovaHub 在 Docker Compose 中遇到的端口冲突、健康检查、中文编码和搜索索引问题。重点是把“能启动”推进到“可复现演示”：MySQL 使用 13306 避开本机端口冲突，前端通过 Nginx 访问 8089，后端健康检查走 9080。排障过程中也补齐了演示数据和热榜回落逻辑。',
   '', NULL, NULL, 2, 1, '演示数据自动通过',
   8, 4, 3, 168, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY, 0),
  (90002, 9003, 1, '内容社区首页应该展示什么',
   '一个简历项目的首页不应该只证明接口存在，还应该让面试官在 30 秒内理解产品：内容流、热榜、标签、搜索和登录后的互动状态。NovaHub 的演示页采用静态 HTML/CSS/JS，避免 Node 构建链，让 Docker 一键启动更稳定。',
   '', NULL, NULL, 2, 1, '演示数据自动通过',
   11, 5, 2, 220, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY, 0),
  (90003, 9004, 1, '热榜计算：从事件流到可解释分数',
   '点赞、收藏、评论和浏览分别使用不同权重，写入 Redis ZSet 后定时持久化到 content_stats。线上链路优先读 Redis，演示环境如果 Redis 为空，会自动回落到数据库快照，保证 fresh volume 也有可见热榜。',
   '', NULL, NULL, 2, 1, '演示数据自动通过',
   9, 6, 2, 196, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, 0),
  (90004, 9003, 1, '搜索链路为什么先选 standard analyzer',
   '为了保证默认 Elasticsearch 镜像可以开箱即用，NovaHub 演示环境先采用内置 standard analyzer，并保留 keyword 子字段用于精确筛选。后续如果要强化中文召回，可以在自定义 ES 镜像中安装 IK 插件后切换 mapping。',
   '', NULL, NULL, 2, 1, '演示数据自动通过',
   7, 3, 1, 143, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, 0),
  (90005, 9001, 1, '我的第一篇 NovaHub 草稿',
   '这是一篇草稿，只会在登录 demo_user 后的草稿列表中出现，用来演示草稿和公开内容的状态差异。',
   '', NULL, NULL, 0, 1, NULL,
   0, 0, 0, 0, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, NULL, 0)
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `content` = VALUES(`content`),
  `status` = VALUES(`status`),
  `review_status` = VALUES(`review_status`),
  `review_remark` = VALUES(`review_remark`),
  `like_count` = VALUES(`like_count`),
  `collect_count` = VALUES(`collect_count`),
  `comment_count` = VALUES(`comment_count`),
  `view_count` = VALUES(`view_count`),
  `publish_time` = VALUES(`publish_time`),
  `is_deleted` = 0;

INSERT INTO `content_tag_rel` (`id`, `content_id`, `tag_id`) VALUES
  (90301, 90001, (SELECT `id` FROM `content_tag` WHERE `name` = '科技')),
  (90302, 90001, (SELECT `id` FROM `content_tag` WHERE `name` = '旅游')),
  (90303, 90002, (SELECT `id` FROM `content_tag` WHERE `name` = '影视')),
  (90304, 90002, (SELECT `id` FROM `content_tag` WHERE `name` = '美食')),
  (90305, 90003, (SELECT `id` FROM `content_tag` WHERE `name` = '科技')),
  (90306, 90003, (SELECT `id` FROM `content_tag` WHERE `name` = '游戏')),
  (90307, 90004, (SELECT `id` FROM `content_tag` WHERE `name` = '科技')),
  (90308, 90005, (SELECT `id` FROM `content_tag` WHERE `name` = '音乐'))
ON DUPLICATE KEY UPDATE `tag_id` = VALUES(`tag_id`);

UPDATE `content_tag` SET `use_count` = 4 WHERE `name` = '科技';
UPDATE `content_tag` SET `use_count` = 1 WHERE `name` IN ('旅游', '影视', '美食', '游戏', '音乐');

INSERT INTO `content_like` (`id`, `user_id`, `content_id`) VALUES
  (90401, 9001, 90001),
  (90402, 9002, 90001),
  (90403, 9003, 90001),
  (90404, 9001, 90002),
  (90405, 9002, 90002),
  (90406, 9004, 90002),
  (90407, 9001, 90003),
  (90408, 9002, 90003),
  (90409, 9003, 90004)
ON DUPLICATE KEY UPDATE `create_time` = `create_time`;

INSERT INTO `content_collect` (`id`, `user_id`, `content_id`, `folder_id`) VALUES
  (90501, 9001, 90001, 90201),
  (90502, 9002, 90001, 90202),
  (90503, 9001, 90002, 90201),
  (90504, 9004, 90002, 90204),
  (90505, 9001, 90003, 90201),
  (90506, 9003, 90004, 90203)
ON DUPLICATE KEY UPDATE `folder_id` = VALUES(`folder_id`);

INSERT INTO `content_comment`
  (`id`, `content_id`, `user_id`, `parent_id`, `root_id`, `content`, `like_count`, `reply_count`, `status`)
VALUES
  (90601, 90001, 9001, NULL, NULL, '这个排障记录很适合放在项目 README 的 FAQ 里，面试时讲起来也清楚。', 3, 1, 1),
  (90602, 90001, 9004, 90601, 90601, '是的，端口冲突和编码问题都是非常真实的工程细节。', 1, 0, 1),
  (90603, 90001, 9002, NULL, NULL, '建议再补一张启动链路图，能快速说明 Nginx、后端和数据库的关系。', 2, 0, 1),
  (90604, 90002, 9001, NULL, NULL, '静态前端不引入构建链这个选择挺务实，演示稳定性优先。', 2, 0, 1),
  (90605, 90002, 9004, NULL, NULL, '内容详情、评论和点赞入口补上后，全栈作品感会明显增强。', 2, 0, 1),
  (90606, 90003, 9003, NULL, NULL, '热度分数最好在文档里写公式，这样不是黑盒。', 4, 0, 1),
  (90607, 90003, 9001, NULL, NULL, '数据库回落很适合演示环境，线上还是应该让 Redis 作为主读路径。', 1, 0, 1),
  (90608, 90004, 9001, NULL, NULL, 'standard analyzer 能保证开箱即用，IK 可以作为后续增强点写进文档。', 2, 0, 1)
ON DUPLICATE KEY UPDATE
  `content` = VALUES(`content`),
  `like_count` = VALUES(`like_count`),
  `reply_count` = VALUES(`reply_count`),
  `status` = VALUES(`status`);

INSERT INTO `content_view` (`id`, `content_id`, `user_id`) VALUES
  (90701, 90001, 9001),
  (90702, 90001, 9002),
  (90703, 90002, 9001),
  (90704, 90003, 9001),
  (90705, 90004, NULL)
ON DUPLICATE KEY UPDATE `create_time` = `create_time`;

INSERT INTO `content_stats`
  (`id`, `content_id`, `user_id`, `type`, `like_count`, `collect_count`, `comment_count`, `view_count`, `heat_score`, `last_update_time`)
VALUES
  (90801, 90001, 9004, 1, 8, 4, 3, 168, 129.7, NOW()),
  (90802, 90002, 9003, 1, 11, 5, 2, 220, 151.4, NOW()),
  (90803, 90003, 9004, 1, 9, 6, 2, 196, 146.9, NOW()),
  (90804, 90004, 9003, 1, 7, 3, 1, 143, 98.5, NOW())
ON DUPLICATE KEY UPDATE
  `user_id` = VALUES(`user_id`),
  `type` = VALUES(`type`),
  `like_count` = VALUES(`like_count`),
  `collect_count` = VALUES(`collect_count`),
  `comment_count` = VALUES(`comment_count`),
  `view_count` = VALUES(`view_count`),
  `heat_score` = VALUES(`heat_score`),
  `last_update_time` = VALUES(`last_update_time`);

INSERT INTO `hot_content_record`
  (`id`, `content_id`, `content_type`, `rank_type`, `rank`, `heat_score`, `record_date`)
VALUES
  (90901, 90002, 1, 0, 1, 151.4, NOW()),
  (90902, 90003, 1, 0, 2, 146.9, NOW()),
  (90903, 90001, 1, 0, 3, 129.7, NOW()),
  (90904, 90004, 1, 0, 4, 98.5, NOW())
ON DUPLICATE KEY UPDATE
  `rank` = VALUES(`rank`),
  `heat_score` = VALUES(`heat_score`),
  `record_date` = VALUES(`record_date`);

INSERT INTO `sys_notification`
  (`id`, `from_user_id`, `to_user_id`, `type`, `content`, `target_id`, `target_type`, `is_read`)
VALUES
  (91001, 9003, 9001, 'FOLLOW', '产品观察员关注了你', 9003, 'USER', 0),
  (91002, 9001, 9004, 'COMMENT', '演示用户评论了你的内容', 90601, 'COMMENT', 0),
  (91003, NULL, 9001, 'SYSTEM', '欢迎使用 NovaHub 演示环境', NULL, 'SYSTEM', 0)
ON DUPLICATE KEY UPDATE
  `content` = VALUES(`content`),
  `is_read` = VALUES(`is_read`),
  `is_deleted` = 0;
