package com.novahub.recommend.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.topic.recommend-behavior:recommend-behavior}")
    private String topic;

    public void sendExposureEvent(Long userId, Long contentId, int position,
                                 String recommendWay, String experimentId,
                                 String bucketId, String requestId) {
        RecommendEvent event = RecommendEvent.ofExposure(
                userId, contentId, position, recommendWay, experimentId, bucketId, requestId
        );
        sendEvent(event, userId.toString());
    }

    public void sendClickEvent(Long userId, Long contentId, String recommendWay) {
        RecommendEvent event = RecommendEvent.ofClick(userId, contentId, recommendWay);
        sendEvent(event, userId.toString());
    }

    private void sendEvent(RecommendEvent event, String key) {
        try {
            String json = com.alibaba.fastjson2.JSON.toJSONString(event);
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topic, key, json);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("发送推荐事件失败: eventType={}, userId={}, error={}",
                            event.getEventType(), event.getUserId(), ex.getMessage());
                } else {
                    log.debug("发送推荐事件成功: eventType={}, userId={}, partition={}, offset={}",
                            event.getEventType(), event.getUserId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("序列化推荐事件失败: {}", e.getMessage(), e);
        }
    }
}
