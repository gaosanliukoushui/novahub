package com.novahub.content.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.content-publish:content-publish}")
    private String contentPublishTopic;

    @Value("${kafka.topic.content-review:content-review}")
    private String contentReviewTopic;

    public void sendReviewSubmitEvent(Long contentId, Long userId) {
        ContentEvent event = ContentEvent.reviewSubmit(contentId, userId);
        try {
            kafkaTemplate.send(contentPublishTopic, String.valueOf(contentId), event);
        } catch (Exception e) {
            log.warn("发送Kafka审核事件失败，内容ID: {}, 错误: {}", contentId, e.getMessage());
        }
    }

    public void sendReviewResultEvent(Long contentId, Boolean approved, String remark) {
        ContentEvent event = ContentEvent.reviewResult(contentId, approved, remark);
        try {
            kafkaTemplate.send(contentReviewTopic, String.valueOf(contentId), event);
        } catch (Exception e) {
            log.warn("发送Kafka审核结果事件失败，内容ID: {}, 错误: {}", contentId, e.getMessage());
        }
    }
}
