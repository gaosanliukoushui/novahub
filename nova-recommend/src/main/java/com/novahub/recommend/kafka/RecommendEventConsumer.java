package com.novahub.recommend.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendEventConsumer {

    @KafkaListener(
            topics = "${spring.kafka.topic.recommend-behavior:recommend-behavior}",
            groupId = "${spring.kafka.group.recommend:recommend-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            String json = record.value();
            RecommendEvent event = com.alibaba.fastjson2.JSON.parseObject(json, RecommendEvent.class);

            log.info("消费推荐事件: eventType={}, userId={}, contentId={}, position={}, recommendWay={}",
                    event.getEventType(),
                    event.getUserId(),
                    event.getContentId(),
                    event.getPosition(),
                    event.getRecommendWay());

            processEvent(event);
        } catch (Exception e) {
            log.error("处理推荐事件失败: {}", e.getMessage(), e);
        }
    }

    private void processEvent(RecommendEvent event) {
        switch (event.getEventType()) {
            case "EXPOSURE" -> handleExposure(event);
            case "CLICK" -> handleClick(event);
            default -> log.warn("未知事件类型: {}", event.getEventType());
        }
    }

    private void handleExposure(RecommendEvent event) {
        // 曝光事件处理：更新推荐系统统计指标
        log.debug("处理曝光事件: userId={}, contentId={}, position={}",
                event.getUserId(), event.getContentId(), event.getPosition());
    }

    private void handleClick(RecommendEvent event) {
        // 点击事件处理：更新推荐系统统计指标
        log.debug("处理点击事件: userId={}, contentId={}",
                event.getUserId(), event.getContentId());
    }
}
