package com.novahub.notify.consumer;

import com.novahub.hotrank.event.ContentStatsEvent;
import com.novahub.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "content-stats", groupId = "nova-notify-group")
    public void consume(ContentStatsEvent event) {
        if (event == null) return;

        try {
            Integer eventTypeInt = event.getEventType();
            Long toUserId = event.getUserId();
            Long fromUserId = event.getActorUserId();
            Long contentId = event.getContentId();

            if (toUserId == null || fromUserId == null || toUserId.equals(fromUserId)) {
                return;
            }

            switch (eventTypeInt) {
                case 1 -> notificationService.sendLikeNotify(fromUserId, toUserId, contentId);
                case 2 -> notificationService.sendCommentNotify(fromUserId, toUserId, null, contentId);
                case 3 -> notificationService.sendFollowNotify(fromUserId, toUserId);
                default -> log.debug("未处理通知事件类型: {}", eventTypeInt);
            }

            log.debug("NotifyEvent consumed: type={}, from={}, to={}",
                    eventTypeInt, fromUserId, toUserId);
        } catch (Exception e) {
            log.error("消费 NotifyEvent 失败: {}", e.getMessage(), e);
        }
    }
}
