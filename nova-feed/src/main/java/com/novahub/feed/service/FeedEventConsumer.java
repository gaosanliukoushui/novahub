package com.novahub.feed.service;

import com.novahub.feed.event.FeedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedEventConsumer {

    private final FeedService feedService;

    public static final String TOPIC = "feed-push";
    public static final String GROUP_ID = "feed-consumer-group";

    @KafkaListener(
            topics = TOPIC,
            groupId = GROUP_ID,
            containerFactory = "feedKafkaListenerContainerFactory"
    )
    public void consumeFeedEvent(FeedEvent event) {
        try {
            if (event.getEventType() == FeedEvent.TYPE_PUBLISH) {
                feedService.pushToFollowers(
                        event.getUserId(),
                        event.getContentId(),
                        event.getContentType(),
                        event.getEventTime()
                );
                log.info("Feed推送事件消费成功: contentId={}, userId={}",
                        event.getContentId(), event.getUserId());
            } else if (event.getEventType() == FeedEvent.TYPE_DELETE) {
                feedService.removeFromFollowers(event.getUserId(), event.getContentId());
                log.info("Feed删除事件消费成功: contentId={}", event.getContentId());
            }
        } catch (Exception e) {
            log.error("Feed事件消费失败: contentId={}, error={}",
                    event.getContentId(), e.getMessage(), e);
        }
    }
}
