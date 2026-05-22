package com.novahub.hotrank.service;

import com.novahub.hotrank.event.ContentStatsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsEventProducer {

    private static final String TOPIC = "content-stats";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaTemplate<String, Object> getTemplate() {
        if (kafkaTemplate == null) {
            Map<String, Object> configProps = new HashMap<>();
            configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
            configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5000);
            configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
            DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(configProps);
            kafkaTemplate = new KafkaTemplate<>(factory);
        }
        return kafkaTemplate;
    }

    private void sendFireAndForget(ContentStatsEvent event) {
        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    TOPIC, String.valueOf(event.getContentId()), event);
            getTemplate().send(record);
        } catch (Exception e) {
            log.warn("Stats event send failed (fire-and-forget): contentId={}, eventType={}, error={}",
                    event.getContentId(), event.getEventType(), e.getMessage());
        }
    }

    public void sendStatsEvent(ContentStatsEvent event) {
        sendFireAndForget(event);
    }

    public void sendLikeEvent(Long contentId, Long userId, Integer contentType, Long actorUserId) {
        sendFireAndForget(ContentStatsEvent.builder()
                .contentId(contentId)
                .userId(userId)
                .contentType(contentType)
                .eventType(1)
                .actorUserId(actorUserId)
                .build());
    }

    public void sendUnlikeEvent(Long contentId, Long userId, Integer contentType, Long actorUserId) {
        sendFireAndForget(ContentStatsEvent.builder()
                .contentId(contentId)
                .userId(userId)
                .contentType(contentType)
                .eventType(2)
                .actorUserId(actorUserId)
                .build());
    }

    public void sendCollectEvent(Long contentId, Long userId, Integer contentType, Long actorUserId) {
        sendFireAndForget(ContentStatsEvent.builder()
                .contentId(contentId)
                .userId(userId)
                .contentType(contentType)
                .eventType(3)
                .actorUserId(actorUserId)
                .build());
    }

    public void sendCommentEvent(Long contentId, Long userId, Integer contentType, Long actorUserId) {
        sendFireAndForget(ContentStatsEvent.builder()
                .contentId(contentId)
                .userId(userId)
                .contentType(contentType)
                .eventType(5)
                .actorUserId(actorUserId)
                .build());
    }

    public void sendViewEvent(Long contentId, Long userId, Integer contentType, Long actorUserId) {
        sendFireAndForget(ContentStatsEvent.builder()
                .contentId(contentId)
                .userId(userId)
                .contentType(contentType)
                .eventType(7)
                .actorUserId(actorUserId)
                .build());
    }
}
