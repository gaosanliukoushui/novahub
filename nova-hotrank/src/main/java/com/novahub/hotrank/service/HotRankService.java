package com.novahub.hotrank.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.novahub.hotrank.entity.ContentStats;
import com.novahub.hotrank.event.ContentStatsEvent;
import com.novahub.hotrank.mapper.ContentStatsMapper;
import com.novahub.hotrank.vo.HotRankVO;
import com.novahub.hotrank.enums.RankType;
import com.novahub.common.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotRankService {

    private final ContentStatsMapper contentStatsMapper;
    private final RedisUtils redisUtils;
    private final Cache<String, List<HotRankVO>> hotContentCache;

    private static final String HEAT_SCORE_KEY = "hotrank:score:%d";
    private static final String HOT_LIST_KEY_PREFIX = "hotrank:list:";
    private static final String HOT_LIST_CACHE_KEY = "hotrank:cache:all:%d";
    private static final String HOT_LIST_TYPE_CACHE_KEY = "hotrank:cache:type:%d:%d";

    private static final double WEIGHT_LIKE = 3.0;
    private static final double WEIGHT_COLLECT = 4.0;
    private static final double WEIGHT_COMMENT = 5.0;
    private static final double WEIGHT_VIEW = 1.0;
    private static final double DECAY_FACTOR_HOUR = 0.95;

    public void processStatsEvent(ContentStatsEvent event) {
        Long contentId = event.getContentId();
        Integer eventType = event.getEventType();
        String scoreKey = String.format(HEAT_SCORE_KEY, contentId);

        Double currentScore = redisUtils.zScore(HOT_LIST_KEY_PREFIX + "all", String.valueOf(contentId));
        if (currentScore == null) {
            currentScore = 0.0;
        }

        double delta = calculateDelta(eventType);
        double timeDecay = calculateTimeDecay(event.getEventTime());
        double newScore = (currentScore + delta) * timeDecay;

        redisUtils.zAdd(HOT_LIST_KEY_PREFIX + "all", String.valueOf(contentId), newScore);

        Integer contentType = event.getContentType();
        if (contentType != null) {
            String typeKey = contentType == 1
                    ? HOT_LIST_KEY_PREFIX + "post"
                    : HOT_LIST_KEY_PREFIX + "video";
            redisUtils.zAdd(typeKey, String.valueOf(contentId), newScore);
        }

        updateRedisCounter(contentId, eventType);

        String cacheKey = String.format(HOT_LIST_CACHE_KEY, contentId);
        if (Boolean.TRUE.equals(redisUtils.hasKey(cacheKey))) {
            redisUtils.delete(cacheKey);
        }

        log.debug("热度更新: contentId={}, eventType={}, oldScore={}, delta={}, newScore={}",
                contentId, eventType, currentScore, delta, newScore);
    }

    @Transactional
    public void persistStats(Long contentId, Integer contentType) {
        String scoreKey = String.format(HEAT_SCORE_KEY, contentId);
        Double score = redisUtils.zScore(HOT_LIST_KEY_PREFIX + "all", String.valueOf(contentId));
        if (score == null) score = 0.0;

        ContentStats existing = contentStatsMapper.selectByContentId(contentId);
        ContentStats stats = new ContentStats();
        stats.setContentId(contentId);
        stats.setType(contentType);
        stats.setHeatScore(score);
        stats.setLastUpdateTime(LocalDateTime.now());

        String likeKey = "hotrank:count:like:" + contentId;
        String collectKey = "hotrank:count:collect:" + contentId;
        String commentKey = "hotrank:count:comment:" + contentId;
        String viewKey = "hotrank:count:view:" + contentId;

        stats.setLikeCount(parseCount(redisUtils.get(likeKey)));
        stats.setCollectCount(parseCount(redisUtils.get(collectKey)));
        stats.setCommentCount(parseCount(redisUtils.get(commentKey)));
        stats.setViewCount(parseCount(redisUtils.get(viewKey)));

        if (existing != null) {
            stats.setId(existing.getId());
            stats.setUserId(existing.getUserId());
            contentStatsMapper.updateById(stats);
        } else {
            contentStatsMapper.insert(stats);
        }
    }

    public List<HotRankVO> getHotRankList(RankType rankType, int limit) {
        String l1Key = getCacheKey(rankType, limit);

        List<HotRankVO> cached = hotContentCache.getIfPresent(l1Key);
        if (cached != null) {
            log.debug("热榜 Caffeine L1 命中: rankType={}, limit={}", rankType, limit);
            return cached;
        }

        String rankKey = HOT_LIST_KEY_PREFIX + getRankKeySuffix(rankType);
        String l2Key = "hotrank:cache:l2:" + rankType.getCode() + ":" + limit;

        String l2Cached = redisUtils.get(l2Key);
        if (l2Cached != null && !l2Cached.isEmpty()) {
            log.debug("热榜 Redis L2 命中: rankType={}, limit={}", rankType, limit);
        }

        Set<String> contentIds = redisUtils.zReverseRange(rankKey, 0, limit - 1);
        if (contentIds == null || contentIds.isEmpty()) {
            List<HotRankVO> fallback = getHotRankFromDB(rankType, limit);
            if (!fallback.isEmpty()) {
                hotContentCache.put(l1Key, fallback);
            }
            return fallback;
        }

        List<HotRankVO> result = new ArrayList<>();
        int rank = 1;
        for (String contentIdStr : contentIds) {
            Long contentId = Long.parseLong(contentIdStr);
            Double score = redisUtils.zScore(rankKey, contentIdStr);
            ContentStats stats = contentStatsMapper.selectByContentId(contentId);
            HotRankVO.HotRankVOBuilder builder = HotRankVO.builder()
                    .contentId(contentId)
                    .rank(rank++)
                    .heatScore(score != null ? score : 0.0);
            if (stats != null) {
                builder.userId(stats.getUserId())
                        .contentType(stats.getType())
                        .likeCount(stats.getLikeCount())
                        .collectCount(stats.getCollectCount())
                        .commentCount(stats.getCommentCount())
                        .viewCount(stats.getViewCount());
            }
            result.add(builder.build());
        }

        if (!result.isEmpty()) {
            redisUtils.set(l2Key, "1", 5, TimeUnit.MINUTES);
            hotContentCache.put(l1Key, result);
        }

        return result;
    }

    public List<HotRankVO> getHotRankFromDB(RankType rankType, int limit) {
        List<ContentStats> statsList;
        if (rankType == RankType.ALL || rankType == RankType.TRENDING) {
            statsList = contentStatsMapper.selectTopByHeatScore(limit);
        } else {
            Integer contentType = rankType == RankType.POST ? 1 : 2;
            statsList = contentStatsMapper.selectTopByTypeAndHeatScore(contentType, limit);
        }

        List<HotRankVO> result = new ArrayList<>();
        int rank = 1;
        for (ContentStats stats : statsList) {
            result.add(HotRankVO.builder()
                    .contentId(stats.getContentId())
                    .userId(stats.getUserId())
                    .rank(rank++)
                    .heatScore(stats.getHeatScore())
                    .likeCount(stats.getLikeCount())
                    .collectCount(stats.getCollectCount())
                    .commentCount(stats.getCommentCount())
                    .viewCount(stats.getViewCount())
                    .contentType(stats.getType())
                    .build());
        }
        return result;
    }

    @Async
    public void fullRecalculateRank() {
        log.info("开始全量重算热榜...");
        long start = System.currentTimeMillis();

        List<ContentStats> allStats = contentStatsMapper.selectTopByHeatScore(1000);

        redisUtils.delete(HOT_LIST_KEY_PREFIX + "all");
        redisUtils.delete(HOT_LIST_KEY_PREFIX + "post");
        redisUtils.delete(HOT_LIST_KEY_PREFIX + "video");

        for (ContentStats stats : allStats) {
            redisUtils.zAdd(HOT_LIST_KEY_PREFIX + "all", String.valueOf(stats.getContentId()), stats.getHeatScore());
            if (stats.getType() != null) {
                String typeKey = stats.getType() == 1
                        ? HOT_LIST_KEY_PREFIX + "post"
                        : HOT_LIST_KEY_PREFIX + "video";
                redisUtils.zAdd(typeKey, String.valueOf(stats.getContentId()), stats.getHeatScore());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("热榜全量重算完成，耗时={}ms，处理记录数={}", elapsed, allStats.size());
    }

    public int prewarmFromDatabase(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        log.info("开始从 content_stats 预热热榜: limit={}", safeLimit);

        List<ContentStats> allStats = contentStatsMapper.selectTopByHeatScore(safeLimit);
        redisUtils.delete(HOT_LIST_KEY_PREFIX + "all");
        redisUtils.delete(HOT_LIST_KEY_PREFIX + "post");
        redisUtils.delete(HOT_LIST_KEY_PREFIX + "video");
        hotContentCache.invalidateAll();

        for (ContentStats stats : allStats) {
            double score = stats.getHeatScore() != null ? stats.getHeatScore() : 0.0;
            String contentId = String.valueOf(stats.getContentId());
            redisUtils.zAdd(HOT_LIST_KEY_PREFIX + "all", contentId, score);
            if (stats.getType() != null) {
                redisUtils.zAdd(HOT_LIST_KEY_PREFIX + (stats.getType() == 1 ? "post" : "video"), contentId, score);
            }
        }

        log.info("热榜预热完成: records={}", allStats.size());
        return allStats.size();
    }

    @jakarta.annotation.PostConstruct
    public void prewarmOnStartup() {
        try {
            Set<String> existing = redisUtils.zReverseRange(HOT_LIST_KEY_PREFIX + "all", 0, 0);
            if (existing == null || existing.isEmpty()) {
                prewarmFromDatabase(100);
            }
        } catch (Exception e) {
            log.warn("热榜启动预热失败，将在首次查询时回落数据库: {}", e.getMessage());
        }
    }

    private double calculateDelta(int eventType) {
        return switch (eventType) {
            case 1 -> WEIGHT_LIKE;
            case 3 -> WEIGHT_COLLECT;
            case 5 -> WEIGHT_COMMENT;
            case 7 -> WEIGHT_VIEW;
            case 2 -> -WEIGHT_LIKE;
            case 4 -> -WEIGHT_COLLECT;
            case 6 -> -WEIGHT_COMMENT;
            default -> 0.0;
        };
    }

    private double calculateTimeDecay(LocalDateTime eventTime) {
        if (eventTime == null) {
            return 1.0;
        }
        long hoursElapsed = Duration.between(eventTime, LocalDateTime.now()).toHours();
        if (hoursElapsed < 0) hoursElapsed = 0;
        return Math.pow(DECAY_FACTOR_HOUR, hoursElapsed);
    }

    private void updateRedisCounter(Long contentId, int eventType) {
        String key;
        switch (eventType) {
            case 1, 2 -> key = "hotrank:count:like:" + contentId;
            case 3, 4 -> key = "hotrank:count:collect:" + contentId;
            case 5, 6 -> key = "hotrank:count:comment:" + contentId;
            case 7 -> key = "hotrank:count:view:" + contentId;
            default -> { return; }
        }
        String current = redisUtils.get(key);
        int count = current != null ? Integer.parseInt(current) : 0;
        int delta = (eventType == 2 || eventType == 4 || eventType == 6) ? -1 : 1;
        int newCount = Math.max(0, count + delta);
        redisUtils.set(key, String.valueOf(newCount));
    }

    private String getRankKeySuffix(RankType rankType) {
        return switch (rankType) {
            case ALL, TRENDING, DAILY, WEEKLY -> "all";
            case POST -> "post";
            case VIDEO -> "video";
        };
    }

    private String getCacheKey(RankType rankType, int limit) {
        return String.format(HOT_LIST_CACHE_KEY, rankType.getCode(), limit);
    }

    private int parseCount(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
