package com.novahub.recommend.algorithm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novahub.common.utils.RedisUtils;
import com.novahub.content.entity.Content;
import com.novahub.content.entity.ContentTag;
import com.novahub.content.entity.ContentTagRel;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.content.mapper.ContentTagMapper;
import com.novahub.content.mapper.ContentTagRelMapper;
import com.novahub.interaction.entity.ContentLike;
import com.novahub.interaction.mapper.ContentLikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于内容的推荐引擎 (Content-Based Filtering)
 *
 * 核心原理：
 * 1. 构建用户画像（基于用户喜欢的内容的标签分布）
 * 2. 为每个候选内容计算与用户画像的匹配度
 * 3. 结合热度分进行综合排序
 *
 * 匹配度计算：
 * match_score = Σ(tag_weight × user_preference[tag])
 *
 * 最终分：
 * final_score = match_score × α + heat_score × (1 - α)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentBasedEngine {

    private final RedisUtils redisUtils;
    private final ContentMapper contentMapper;
    private final ContentTagMapper contentTagMapper;
    private final ContentTagRelMapper contentTagRelMapper;
    private final ContentLikeMapper contentLikeMapper;

    private static final String USER_TAG_PROFILE_KEY = "recommend:cb:profile:%d";
    private static final String CONTENT_TAGS_KEY = "recommend:content:tags:%d";
    private static final String TAG_CONTENTS_KEY = "recommend:tag:contents:%d";

    private static final double ALPHA = 0.7;
    private static final int CANDIDATE_SIZE = 200;

    /**
     * 为指定用户生成基于内容的推荐列表
     */
    public List<Long> generateRecommendations(Long userId, int limit, Integer contentType) {
        if (userId == null) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();

        // 1. 获取用户标签偏好画像
        Map<Long, Double> userProfile = getUserTagProfile(userId);
        if (userProfile.isEmpty()) {
            log.debug("用户 {} 无标签偏好，使用热门内容 fallback", userId);
            return fallbackToHotContent(limit, contentType);
        }

        // 2. 获取候选内容（用户未点赞的内容）
        Set<Long> userLiked = getUserLikedContentIds(userId);
        List<Content> candidates = getCandidateContents(userId, userLiked, contentType, CANDIDATE_SIZE);

        if (candidates.isEmpty()) {
            return fallbackToHotContent(limit, contentType);
        }

        // 3. 计算每个候选内容的匹配分
        List<CandidateContent> scored = new ArrayList<>();
        for (Content content : candidates) {
            Set<Long> contentTags = getContentTags(content.getId());
            double matchScore = calculateMatchScore(contentTags, userProfile);
            double heatScore = calculateHeatScore(content);
            double finalScore = matchScore * ALPHA + heatScore * (1 - ALPHA);

            scored.add(new CandidateContent(content.getId(), finalScore, matchScore));
        }

        // 4. 排序并返回
        List<Long> result = scored.stream()
                .sorted(Comparator.comparingDouble(CandidateContent::getScore).reversed())
                .limit(limit)
                .map(CandidateContent::getContentId)
                .collect(Collectors.toList());

        long cost = System.currentTimeMillis() - startTime;
        log.info("基于内容推荐完成: userId={}, 候选数={}, 结果数={}, 耗时={}ms",
                userId, candidates.size(), result.size(), cost);

        return result;
    }

    /**
     * 构建并缓存用户标签偏好画像
     */
    public Map<Long, Double> buildAndCacheUserTagProfile(Long userId) {
        // 从数据库获取用户点赞过的内容及其标签
        LambdaQueryWrapper<ContentLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(ContentLike::getUserId, userId);
        List<ContentLike> likes = contentLikeMapper.selectList(likeWrapper);

        if (likes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> likedContentIds = likes.stream()
                .map(ContentLike::getContentId)
                .collect(Collectors.toList());

        // 统计标签出现次数
        Map<Long, Integer> tagCounts = new HashMap<>();
        for (Long contentId : likedContentIds) {
            Set<Long> tags = getContentTags(contentId);
            for (Long tagId : tags) {
                tagCounts.merge(tagId, 1, Integer::sum);
            }
        }

        // 计算归一化的权重
        int total = tagCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            return Collections.emptyMap();
        }

        Map<Long, Double> profile = new HashMap<>();
        for (Map.Entry<Long, Integer> entry : tagCounts.entrySet()) {
            profile.put(entry.getKey(), (double) entry.getValue() / total);
        }

        // 缓存到 Redis
        String key = String.format(USER_TAG_PROFILE_KEY, userId);
        Map<String, String> profileStr = profile.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString()
                ));
        redisUtils.hSetAll(key, profileStr);
        redisUtils.expire(key, 7L, java.util.concurrent.TimeUnit.DAYS);

        log.info("构建用户标签画像: userId={}, 标签数={}", userId, profile.size());
        return profile;
    }

    /**
     * 获取用户标签偏好画像（从缓存或重建）
     */
    public Map<Long, Double> getUserTagProfile(Long userId) {
        String key = String.format(USER_TAG_PROFILE_KEY, userId);
        Map<Object, Object> cached = redisUtils.hGetAll(key);

        if (cached != null && !cached.isEmpty()) {
            return cached.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> Long.parseLong(e.getKey().toString()),
                            e -> Double.parseDouble(e.getValue().toString())
                    ));
        }

        return buildAndCacheUserTagProfile(userId);
    }

    /**
     * 获取内容的标签集合
     */
    public Set<Long> getContentTags(Long contentId) {
        String key = String.format(CONTENT_TAGS_KEY, contentId);
        Set<String> cached = redisUtils.sMembers(key);

        if (cached != null && !cached.isEmpty()) {
            return cached.stream().map(Long::parseLong).collect(Collectors.toSet());
        }

        LambdaQueryWrapper<ContentTagRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContentTagRel::getContentId, contentId);
        List<ContentTagRel> rels = contentTagRelMapper.selectList(wrapper);

        Set<Long> tagIds = rels.stream()
                .map(ContentTagRel::getTagId)
                .collect(Collectors.toSet());

        if (!tagIds.isEmpty()) {
            String[] tagIdStrs = tagIds.stream().map(String::valueOf).toArray(String[]::new);
            redisUtils.sAdd(key, tagIdStrs);
            redisUtils.expire(key, 1L, java.util.concurrent.TimeUnit.HOURS);
        }

        return tagIds;
    }

    /**
     * 计算内容与用户画像的匹配分
     */
    private double calculateMatchScore(Set<Long> contentTags, Map<Long, Double> userProfile) {
        if (contentTags.isEmpty() || userProfile.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;
        for (Long tagId : contentTags) {
            Double weight = userProfile.get(tagId);
            if (weight != null) {
                score += weight;
            }
        }

        return score;
    }

    /**
     * 计算内容的热度分（时间衰减）
     */
    private double calculateHeatScore(Content content) {
        int likeCount = content.getLikeCount() != null ? content.getLikeCount() : 0;
        int commentCount = content.getCommentCount() != null ? content.getCommentCount() : 0;
        int viewCount = content.getViewCount() != null ? content.getViewCount() : 0;

        double heatBase = likeCount * 3.0 + commentCount * 5.0 + viewCount * 1.0;

        LocalDateTime publishTime = content.getPublishTime() != null
                ? content.getPublishTime()
                : content.getCreateTime();

        if (publishTime == null) {
            return heatBase;
        }

        long hoursAgo = LocalDateTime.now().toInstant(ZoneOffset.ofHours(8))
                .toEpochMilli() - publishTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        hoursAgo = hoursAgo / (1000 * 60 * 60);

        if (hoursAgo < 1) {
            return heatBase;
        }

        double decay = 1.0 / (1.0 + 0.05 * hoursAgo);
        return heatBase * decay;
    }

    /**
     * 获取候选内容列表
     */
    private List<Content> getCandidateContents(Long userId, Set<Long> excludeContentIds,
                                               Integer contentType, int limit) {
        LambdaQueryWrapper<Content> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Content::getIsDeleted, 0)
                .eq(Content::getStatus, 2)
                .orderByDesc(Content::getPublishTime, Content::getLikeCount);

        if (contentType != null) {
            wrapper.eq(Content::getType, contentType);
        }

        if (!excludeContentIds.isEmpty()) {
            wrapper.notIn(Content::getId, excludeContentIds);
        }

        wrapper.last("LIMIT " + limit);
        return contentMapper.selectList(wrapper);
    }

    /**
     * 获取用户点赞过的内容ID集合
     */
    private Set<Long> getUserLikedContentIds(Long userId) {
        LambdaQueryWrapper<ContentLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContentLike::getUserId, userId);
        List<ContentLike> likes = contentLikeMapper.selectList(wrapper);

        return likes.stream()
                .map(ContentLike::getContentId)
                .collect(Collectors.toSet());
    }

    /**
     * Fallback 到热门内容
     */
    private List<Long> fallbackToHotContent(int limit, Integer contentType) {
        LambdaQueryWrapper<Content> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Content::getIsDeleted, 0)
                .eq(Content::getStatus, 2)
                .orderByDesc(Content::getLikeCount, Content::getCreateTime);

        if (contentType != null) {
            wrapper.eq(Content::getType, contentType);
        }

        wrapper.last("LIMIT " + limit);

        return contentMapper.selectList(wrapper).stream()
                .map(Content::getId)
                .collect(Collectors.toList());
    }

    /**
     * 候选内容项
     */
    private static class CandidateContent {
        private final Long contentId;
        private final double score;
        private final double matchScore;

        public CandidateContent(Long contentId, double score, double matchScore) {
            this.contentId = contentId;
            this.score = score;
            this.matchScore = matchScore;
        }

        public Long getContentId() {
            return contentId;
        }

        public double getScore() {
            return score;
        }

        public double getMatchScore() {
            return matchScore;
        }
    }
}
