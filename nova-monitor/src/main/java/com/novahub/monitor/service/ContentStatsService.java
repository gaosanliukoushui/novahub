package com.novahub.monitor.service;

import com.novahub.content.entity.Content;
import com.novahub.content.mapper.ContentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentStatsService {

    private final ContentMapper contentMapper;
    private final com.novahub.common.utils.RedisUtils redisUtils;

    public static final String TOP_CONTENT_KEY = "top:content:like:";
    public static final String TOP_VIDEO_KEY = "top:video:like:";

    public void updateTopContent() {
        List<Content> posts = contentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Content>()
                        .eq(Content::getStatus, 2)
                        .eq(Content::getType, 1)
                        .orderByDesc(Content::getLikeCount)
                        .last("LIMIT 100"));

        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = TOP_CONTENT_KEY + today;
        redisUtils.delete(key);

        for (Content c : posts) {
            redisUtils.zAdd(key, c.getId().toString(), c.getLikeCount() != null ? c.getLikeCount() : 0);
        }
        log.info("TOP内容更新完成，共{}条", posts.size());
    }

    public List<Map<String, Object>> getTopContent(String type, int limit) {
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "like".equals(type) ? (TOP_CONTENT_KEY + today) : (TOP_VIDEO_KEY + today);

        Set<ZSetOperations.TypedTuple<String>> top = redisUtils.zReverseRangeWithScores(key, 0, limit - 1);
        if (top == null || top.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ids = top.stream()
                .map(t -> Long.parseLong(Objects.requireNonNull(t.getValue())))
                .collect(Collectors.toList());

        List<Content> contents = contentMapper.selectBatchIds(ids);
        Map<Long, Content> contentMap = contents.stream()
                .collect(Collectors.toMap(Content::getId, c -> c));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> t : top) {
            Long id = Long.parseLong(Objects.requireNonNull(t.getValue()));
            Content c = contentMap.get(id);
            if (c == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("title", c.getTitle());
            item.put("likeCount", c.getLikeCount());
            item.put("commentCount", c.getCommentCount());
            item.put("viewCount", c.getViewCount());
            item.put("publishTime", c.getPublishTime());
            result.add(item);
        }
        return result;
    }
}
