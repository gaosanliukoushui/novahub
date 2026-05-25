package com.novahub.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novahub.common.entity.EventOutbox;
import com.novahub.common.mapper.EventOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventOutboxService {

    private final EventOutboxMapper eventOutboxMapper;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${outbox.dispatch.enabled:true}")
    private boolean dispatchEnabled;

    @Value("${outbox.dispatch.batch-size:50}")
    private int batchSize;

    public void record(String eventType, String aggregateType, Long aggregateId, String topic, Object payload) {
        try {
            EventOutbox event = new EventOutbox();
            event.setEventType(eventType);
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setTopic(topic);
            event.setEventKey(aggregateId == null ? null : String.valueOf(aggregateId));
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setStatus(0);
            event.setRetryCount(0);
            event.setCreateTime(LocalDateTime.now());
            event.setUpdateTime(LocalDateTime.now());
            eventOutboxMapper.insert(event);
        } catch (Exception e) {
            log.warn("记录 outbox 事件失败: eventType={}, aggregateId={}, error={}",
                    eventType, aggregateId, e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${outbox.dispatch.fixed-delay-ms:10000}")
    public void dispatchPendingEvents() {
        if (!dispatchEnabled) {
            return;
        }
        List<EventOutbox> events = eventOutboxMapper.selectList(
                new LambdaQueryWrapper<EventOutbox>()
                        .in(EventOutbox::getStatus, 0, 2)
                        .and(wrapper -> wrapper
                                .isNull(EventOutbox::getNextRetryTime)
                                .or()
                                .le(EventOutbox::getNextRetryTime, LocalDateTime.now()))
                        .orderByAsc(EventOutbox::getCreateTime)
                        .last("LIMIT " + Math.max(1, batchSize))
        );

        for (EventOutbox event : events) {
            dispatchOne(event);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void dispatchOne(EventOutbox event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get(5, TimeUnit.SECONDS);
            event.setStatus(1);
            event.setErrorMessage(null);
            event.setUpdateTime(LocalDateTime.now());
            eventOutboxMapper.updateById(event);
        } catch (Exception e) {
            int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
            event.setStatus(2);
            event.setRetryCount(retryCount + 1);
            event.setErrorMessage(trimError(e.getMessage()));
            event.setNextRetryTime(LocalDateTime.now().plusSeconds(Math.min(300, 10L * (retryCount + 1))));
            event.setUpdateTime(LocalDateTime.now());
            eventOutboxMapper.updateById(event);
            log.warn("outbox 事件投递失败: id={}, topic={}, retry={}, error={}",
                    event.getId(), event.getTopic(), event.getRetryCount(), e.getMessage());
        }
    }

    private String trimError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 512 ? message.substring(0, 512) : message;
    }
}
