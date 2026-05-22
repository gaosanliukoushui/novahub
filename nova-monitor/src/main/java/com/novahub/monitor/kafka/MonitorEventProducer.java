package com.novahub.monitor.kafka;

import com.novahub.common.utils.RedisUtils;
import com.novahub.monitor.event.MonitorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisUtils redisUtils;

    public static final String TOPIC = "activity-log";
    private static final String PV_KEY_PREFIX = "pv:";
    private static final String UV_KEY_PREFIX = "uv:";
    private static final String DAU_KEY_PREFIX = "dau:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public void sendEvent(MonitorEvent event) {
        String eventId = java.util.UUID.randomUUID().toString().replace("-", "");
        event.setEventId(eventId);
        if (event.getTimestamp() == null) {
            event.setTimestamp(System.currentTimeMillis());
        }

        kafkaTemplate.send(TOPIC, event.getUserId() != null ? event.getUserId().toString() : eventId, event);

        String dateKey = LocalDate.now().format(DATE_FMT);
        redisUtils.incr(PV_KEY_PREFIX + dateKey);

        if (event.getUserId() != null) {
            redisUtils.pfAdd(UV_KEY_PREFIX + dateKey, event.getUserId().toString());
            redisUtils.pfAdd(DAU_KEY_PREFIX + dateKey, event.getUserId().toString());
        }

        log.debug("MonitorEvent sent: eventId={}, type={}", eventId, event.getEventType());
    }

    public void sendPageViewEvent(Long userId, String page, String sessionId, String device) {
        MonitorEvent event = MonitorEvent.builder()
                .eventType(MonitorEvent.EventType.PAGE_VIEW.getValue())
                .userId(userId)
                .page(page)
                .sessionId(sessionId)
                .device(device)
                .timestamp(System.currentTimeMillis())
                .build();
        sendEvent(event);
    }
}
