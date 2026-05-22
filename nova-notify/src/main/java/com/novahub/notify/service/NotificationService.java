package com.novahub.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novahub.common.utils.RedisUtils;
import com.novahub.content.client.UserClient;
import com.novahub.notify.dto.NotifyMessage;
import com.novahub.notify.entity.Notification;
import com.novahub.notify.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final WebSocketPushService webSocketPushService;
    private final UserClient userClient;
    private final RedisUtils redisUtils;

    private static final String UNREAD_COUNT_KEY = "notify:unread:";

    public void sendLikeNotify(Long fromUserId, Long toUserId, Long contentId) {
        buildAndSend(fromUserId, toUserId, Notification.TYPE_LIKE,
                "赞了你的内容", contentId, "CONTENT");
    }

    public void sendCommentNotify(Long fromUserId, Long toUserId, Long commentId, Long contentId) {
        buildAndSend(fromUserId, toUserId, Notification.TYPE_COMMENT,
                "评论了你的内容", contentId, "CONTENT");
    }

    public void sendFollowNotify(Long fromUserId, Long toUserId) {
        buildAndSend(fromUserId, toUserId, Notification.TYPE_FOLLOW,
                "关注了你", toUserId, "USER");
    }

    private void buildAndSend(Long fromUserId, Long toUserId, String type, String content,
                              Long targetId, String targetType) {
        UserClient.UserInfo fromUser = userClient.getUserInfo(fromUserId);

        Notification notification = new Notification();
        notification.setFromUserId(fromUserId);
        notification.setToUserId(toUserId);
        notification.setType(type);
        notification.setContent(content);
        notification.setTargetId(targetId);
        notification.setTargetType(targetType);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);

        NotifyMessage message = NotifyMessage.builder()
                .type(type)
                .id(notification.getId())
                .fromUserId(fromUserId)
                .fromUserNickname(fromUser != null ? fromUser.getNickname() : null)
                .fromUserAvatar(fromUser != null ? fromUser.getAvatar() : null)
                .toUserId(toUserId)
                .content(content)
                .targetId(targetId)
                .targetType(targetType)
                .timestamp(System.currentTimeMillis())
                .build();

        webSocketPushService.pushToUser(message);

        String unreadKey = UNREAD_COUNT_KEY + toUserId;
        redisUtils.incr(unreadKey);
    }

    public Page<Notification> getNotificationList(Long userId, int page, int pageSize) {
        return notificationMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getToUserId, userId)
                        .eq(Notification::getIsDeleted, 0)
                        .orderByDesc(Notification::getCreateTime)
        );
    }

    public long getUnreadCount(Long userId) {
        String key = UNREAD_COUNT_KEY + userId;
        String val = redisUtils.get(key);
        if (val != null) {
            return Long.parseLong(val);
        }
        long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getToUserId, userId)
                        .eq(Notification::getIsDeleted, 0)
                        .eq(Notification::getIsRead, 0)
        );
        redisUtils.set(key, String.valueOf(count));
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getToUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1)
                .set(Notification::getReadTime, LocalDateTime.now());
        notificationMapper.update(null, wrapper);

        String key = UNREAD_COUNT_KEY + userId;
        redisUtils.set(key, "0");
        log.info("全部通知已标记为已读: userId={}", userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationMapper.selectOne(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getId, notificationId)
                        .eq(Notification::getToUserId, userId)
        );

        if (notification != null && notification.getIsRead() == 0) {
            notification.setIsRead(1);
            notification.setReadTime(LocalDateTime.now());
            notificationMapper.updateById(notification);

            String key = UNREAD_COUNT_KEY + userId;
            String val = redisUtils.get(key);
            if (val != null && Long.parseLong(val) > 0) {
                redisUtils.decr(key);
            }
        }
    }
}
