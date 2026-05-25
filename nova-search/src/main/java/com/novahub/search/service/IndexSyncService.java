package com.novahub.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novahub.content.entity.Content;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.content.service.ITagService;
import com.novahub.content.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexSyncService {

    private final ElasticsearchClient esClient;
    private final ContentMapper contentMapper;
    private final ITagService tagService;
    private final IndexService indexService;

    public static final String CONTENT_INDEX = "nova_content";

    public void indexContent(Content content) {
        try {
            Map<String, Object> doc = buildDocument(content);
            esClient.index(IndexRequest.of(i -> i
                    .index(CONTENT_INDEX)
                    .id(String.valueOf(content.getId()))
                    .document(doc)));
            log.debug("内容索引写入成功: contentId={}", content.getId());
        } catch (Exception e) {
            log.error("内容索引写入失败: contentId={}, error={}", content.getId(), e.getMessage(), e);
        }
    }

    public void updateContentIndex(Long contentId, Content content) {
        indexContent(content);
    }

    public void deleteContentIndex(Long contentId) {
        try {
            esClient.delete(DeleteRequest.of(d -> d
                    .index(CONTENT_INDEX)
                    .id(String.valueOf(contentId))));
            log.debug("内容索引删除成功: contentId={}", contentId);
        } catch (Exception e) {
            log.error("内容索引删除失败: contentId={}, error={}", contentId, e.getMessage(), e);
        }
    }

    @Async
    @Scheduled(cron = "0 0 3 * * ?")
    public void buildFullIndex() {
        log.info("开始全量构建内容索引...");
        long start = System.currentTimeMillis();
        String targetIndex = indexService.aliasExists(CONTENT_INDEX)
                ? CONTENT_INDEX + "_rebuild_" + System.currentTimeMillis()
                : CONTENT_INDEX;

        int pageSize = 500;
        int page = 1;
        int totalIndexed = 0;

        try {
            if (CONTENT_INDEX.equals(targetIndex)) {
                indexService.deleteIndex(CONTENT_INDEX);
            }
            indexService.createIndex(targetIndex);

            while (true) {
                List<Content> contents = contentMapper.selectList(
                        new LambdaQueryWrapper<Content>()
                                .eq(Content::getIsDeleted, 0)
                                .eq(Content::getStatus, 2)
                                .last("LIMIT " + pageSize + " OFFSET " + (page - 1) * pageSize)
                );

                if (contents.isEmpty()) {
                    break;
                }

                bulkIndexContents(targetIndex, contents);
                totalIndexed += contents.size();
                log.info("全量索引进度: 第{}页, 本页{}条, 累计{}条",
                        page, contents.size(), totalIndexed);

                if (contents.size() < pageSize) {
                    break;
                }
                page++;
            }

            indexService.refreshIndex(targetIndex);
            if (!CONTENT_INDEX.equals(targetIndex)) {
                indexService.switchAlias(CONTENT_INDEX, targetIndex);
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("全量索引构建完成, targetIndex={}, 共{}条, 耗时{}ms", targetIndex, totalIndexed, elapsed);
        } catch (Exception e) {
            log.error("全量索引构建失败: {}", e.getMessage(), e);
        }
    }

    public void bulkIndexContents(List<Content> contents) {
        bulkIndexContents(CONTENT_INDEX, contents);
    }

    public void bulkIndexContents(String indexName, List<Content> contents) {
        if (contents == null || contents.isEmpty()) return;

        try {
            List<BulkOperation> operations = contents.stream()
                    .map(content -> {
                        Map<String, Object> doc = buildDocument(content);
                        return BulkOperation.of(b -> b
                                .index(IndexOperation.of(i -> i
                                        .index(indexName)
                                        .id(String.valueOf(content.getId()))
                                        .document(doc))));
                    })
                    .collect(Collectors.toList());

            esClient.bulk(BulkRequest.of(b -> b.operations(operations)));
        } catch (IOException e) {
            log.error("批量索引失败: {}", e.getMessage(), e);
        }
    }

    private Map<String, Object> buildDocument(Content content) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", content.getId().toString());
        doc.put("userId", content.getUserId().toString());
        doc.put("type", content.getType());
        doc.put("title", content.getTitle());
        doc.put("content", content.getContent());
        doc.put("likeCount", content.getLikeCount() != null ? content.getLikeCount() : 0);
        doc.put("commentCount", content.getCommentCount() != null ? content.getCommentCount() : 0);
        doc.put("collectCount", content.getCollectCount() != null ? content.getCollectCount() : 0);
        doc.put("viewCount", content.getViewCount() != null ? content.getViewCount() : 0);
        doc.put("status", content.getStatus());

        LocalDateTime createTime = content.getCreateTime();
        LocalDateTime publishTime = content.getPublishTime();
        if (createTime != null) {
            doc.put("createTime", createTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli());
        }
        if (publishTime != null) {
            doc.put("publishTime", publishTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli());
        }

        try {
            List<TagVO> tags = tagService.getTagsByContentId(content.getId());
            if (tags != null && !tags.isEmpty()) {
                doc.put("tagNames", tags.stream().map(TagVO::getName).collect(Collectors.toList()));
                doc.put("tags", tags.stream().map(t -> t.getId().toString()).collect(Collectors.toList()));
            }
        } catch (Exception e) {
            log.warn("获取内容标签失败: contentId={}", content.getId());
        }

        return doc;
    }
}
