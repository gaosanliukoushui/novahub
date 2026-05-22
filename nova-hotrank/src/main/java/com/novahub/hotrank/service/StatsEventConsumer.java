package com.novahub.hotrank.service;

import com.novahub.hotrank.event.ContentStatsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatsEventConsumer {

    private final HotRankService hotRankService;

    public static final String TOPIC = "content-stats";
    public static final String GROUP_ID = "hotrank-stats-group";

    @KafkaListener(
            topics = TOPIC,
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeStatsEvent(ContentStatsEvent event) {
        try {
            log.debug("消费统计事件: contentId={}, eventType={}, actorUserId={}",
                    event.getContentId(), event.getEventType(), event.getActorUserId());

            hotRankService.processStatsEvent(event);

            log.info("统计事件处理成功: contentId={}, eventType={}",
                    event.getContentId(), event.getEventType());
        } catch (Exception e) {
            log.error("处理统计事件失败: contentId={}, eventType={}, error={}",
                    event.getContentId(), event.getEventType(), e.getMessage(), e);
        }
    }
}
