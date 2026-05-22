package com.novahub.monitor.kafka;

import com.novahub.monitor.event.MonitorEvent;
import com.novahub.monitor.service.ActivityStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorEventConsumer {

    private final ActivityStatsService activityStatsService;

    @KafkaListener(topics = MonitorEventProducer.TOPIC, groupId = "nova-monitor-group")
    public void consume(MonitorEvent event) {
        try {
            if (event == null) return;

            String eventType = event.getEventType();
            Long userId = event.getUserId();

            if ("PUBLISH".equals(eventType)) {
                activityStatsService.incrPublishCount();
            } else if ("FOLLOW".equals(eventType)) {
                activityStatsService.incrFollowCount();
            }

            log.debug("MonitorEvent consumed: eventId={}, type={}, userId={}",
                    event.getEventId(), eventType, userId);
        } catch (Exception e) {
            log.error("消费 MonitorEvent 失败: {}", e.getMessage(), e);
        }
    }
}
