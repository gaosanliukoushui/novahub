package com.novahub.content.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentEventConsumer {

    @KafkaListener(topics = "${kafka.topic.content-publish:content-publish}",
                   groupId = "${kafka.group.id:content-group}",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consumeContentPublishEvent(ContentEvent event) {
        log.info("收到内容发布事件: eventType={}, contentId={}, userId={}, eventTime={}",
                event.getEventType(), event.getContentId(), event.getUserId(), event.getEventTime());

        if ("REVIEW_SUBMIT".equals(event.getEventType())) {
            handleReviewSubmitEvent(event);
        } else {
            log.warn("未知的事件类型: {}", event.getEventType());
        }
    }

    @KafkaListener(topics = "${kafka.topic.content-review:content-review}",
                   groupId = "${kafka.group.id:content-group}",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consumeContentReviewEvent(ContentEvent event) {
        log.info("收到内容审核事件: eventType={}, contentId={}, approved={}, remark={}, eventTime={}",
                event.getEventType(), event.getContentId(), event.getApproved(), event.getRemark(), event.getEventTime());

        if ("REVIEW_RESULT".equals(event.getEventType())) {
            handleReviewResultEvent(event);
        } else {
            log.warn("未知的事件类型: {}", event.getEventType());
        }
    }

    private void handleReviewSubmitEvent(ContentEvent event) {
        log.info("处理内容提交审核事件: contentId={}", event.getContentId());
    }

    private void handleReviewResultEvent(ContentEvent event) {
        log.info("处理内容审核结果事件: contentId={}, approved={}, remark={}",
                event.getContentId(), event.getApproved(), event.getRemark());
    }
}
