package com.novahub.feed.service;

import com.novahub.feed.event.FeedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "feed-push";

    @Async
    public void sendPublishEvent(Long contentId, Long userId, Integer contentType) {
        FeedEvent event = FeedEvent.builder()
                .contentId(contentId)
                .userId(userId)
                .contentType(contentType)
                .eventType(FeedEvent.TYPE_PUBLISH)
                .eventTime(LocalDateTime.now())
                .build();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, String.valueOf(contentId), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("发送Feed发布事件失败: contentId={}, error={}", contentId, ex.getMessage());
            } else {
                log.debug("Feed发布事件发送成功: contentId={}, partition={}, offset={}",
                        contentId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    @Async
    public void sendDeleteEvent(Long contentId, Long userId) {
        FeedEvent event = FeedEvent.builder()
                .contentId(contentId)
                .userId(userId)
                .eventType(FeedEvent.TYPE_DELETE)
                .eventTime(LocalDateTime.now())
                .build();

        kafkaTemplate.send(TOPIC, String.valueOf(contentId), event);
        log.debug("Feed删除事件发送成功: contentId={}", contentId);
    }
}
