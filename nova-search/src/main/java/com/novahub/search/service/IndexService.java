package com.novahub.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {

    private final ElasticsearchClient esClient;

    public static final String CONTENT_INDEX = "nova_content";

    private static final String CONTENT_MAPPING = """
            {
              "settings": {
                "number_of_shards": 3,
                "number_of_replicas": 1
              },
              "mappings": {
                "properties": {
                  "id": { "type": "keyword" },
                  "userId": { "type": "keyword" },
                  "authorNickname": {
                    "type": "text",
                    "analyzer": "standard",
                    "fields": {
                      "keyword": { "type": "keyword" }
                    }
                  },
                  "type": { "type": "integer" },
                  "title": {
                    "type": "text",
                    "analyzer": "standard",
                    "fields": {
                      "keyword": { "type": "keyword" }
                    }
                  },
                  "content": {
                    "type": "text",
                    "analyzer": "standard"
                  },
                  "tags": { "type": "keyword" },
                  "tagNames": {
                    "type": "text",
                    "analyzer": "standard",
                    "fields": {
                      "keyword": { "type": "keyword" }
                    }
                  },
                  "likeCount": { "type": "integer" },
                  "commentCount": { "type": "integer" },
                  "collectCount": { "type": "integer" },
                  "viewCount": { "type": "integer" },
                  "status": { "type": "integer" },
                  "createTime": { "type": "date" },
                  "publishTime": { "type": "date" }
                }
              }
            }
            """;

    @PostConstruct
    public void init() {
        try {
            if (!indexExists(CONTENT_INDEX)) {
                createIndex(CONTENT_INDEX);
                log.info("ES索引 {} 创建成功", CONTENT_INDEX);
            } else {
                log.info("ES索引 {} 已存在，跳过创建", CONTENT_INDEX);
            }
        } catch (Exception e) {
            log.error("初始化ES索引失败: {}", e.getMessage(), e);
        }
    }

    public boolean indexExists(String indexName) {
        try {
            return esClient.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        } catch (Exception e) {
            log.error("检查索引是否存在失败: {}", e.getMessage());
            return false;
        }
    }

    public void createIndex(String indexName) {
        try {
            esClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .withJson(new StringReader(CONTENT_MAPPING))));
            log.info("索引 {} 创建成功", indexName);
        } catch (Exception e) {
            log.warn("创建ES索引 {} 失败（ES不可用），搜索功能将降级: {}", indexName, e.getMessage());
        }
    }

    public void deleteIndex(String indexName) {
        try {
            if (indexExists(indexName)) {
                esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(indexName)));
                log.info("索引 {} 删除成功", indexName);
            }
        } catch (Exception e) {
            log.error("删除索引 {} 失败: {}", indexName, e.getMessage(), e);
        }
    }

    public void refreshIndex(String indexName) {
        try {
            esClient.indices().refresh(RefreshRequest.of(r -> r.index(indexName)));
        } catch (Exception e) {
            log.error("刷新索引 {} 失败: {}", indexName, e.getMessage(), e);
        }
    }
}
