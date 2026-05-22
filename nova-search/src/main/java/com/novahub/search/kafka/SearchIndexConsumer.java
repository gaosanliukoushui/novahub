package com.novahub.search.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novahub.content.entity.Content;
import com.novahub.content.kafka.ContentEvent;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.search.service.IndexSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexConsumer {

    private final IndexSyncService indexSyncService;
    private final ContentMapper contentMapper;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "content-review", groupId = "nova-search-group",
            autoStartup = "false")
    public void consumeReviewResult(ContentEvent event) {
        try {
            if (event == null || !"REVIEW_RESULT".equals(event.getEventType())) {
                return;
            }

            Long contentId = event.getContentId();
            log.debug("收到审核结果事件: contentId={}, approved={}", contentId, event.getApproved());

            if (Boolean.TRUE.equals(event.getApproved())) {
                Content content = contentMapper.selectById(contentId);
                if (content != null && content.getStatus() == 2) {
                    indexSyncService.indexContent(content);
                    log.info("审核通过，内容已索引: contentId={}", contentId);
                }
            } else {
                indexSyncService.deleteContentIndex(contentId);
                log.info("审核拒绝，删除索引: contentId={}", contentId);
            }
        } catch (Exception e) {
            log.error("消费审核结果事件失败: {}", e.getMessage(), e);
        }
    }
}
