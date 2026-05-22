package com.novahub.recommend.algorithm;

import com.novahub.common.utils.RedisUtils;
import com.novahub.content.entity.Content;
import com.novahub.content.mapper.ContentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 协同过滤推荐引擎 (User-Based Collaborative Filtering)
 *
 * 核心原理：
 * 1. 找到与目标用户兴趣相似的用户群
 * 2. 从相似用户喜欢的内容中推荐给目标用户
 *
 * 相似度计算：余弦相似度
 * sim(u,v) = |L(u) ∩ L(v)| / sqrt(|L(u)| × |L(v)|)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborativeFilteringEngine {

    private final RedisUtils redisUtils;
    private final ContentMapper contentMapper;

    private static final String USER_BEHAVIOR_KEY = "user:likes:%d";
    private static final String USER_SIMILARITY_KEY = "recommend:cf:similarity:%d";
    private static final String CF_RESULT_KEY = "recommend:cf:result:%d";

    private static final int TOP_K_USERS = 50;
    private static final int CANDIDATE_SIZE = 100;

    /**
     * 为指定用户生成协同过滤推荐列表
     */
    public List<Long> generateRecommendations(Long userId, int limit) {
        if (userId == null) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();

        // 1. 获取用户行为向量（点赞内容列表）
        Set<Long> userLikedContents = getUserBehaviorVector(userId);
        if (userLikedContents.isEmpty()) {
            log.debug("用户 {} 无点赞行为，无法进行协同过滤", userId);
            return Collections.emptyList();
        }

        // 2. 找到相似用户
        Map<Long, Double> similarUsers = findSimilarUsers(userId, userLikedContents);
        if (similarUsers.isEmpty()) {
            log.debug("用户 {} 未找到相似用户", userId);
            return Collections.emptyList();
        }

        // 3. 从相似用户中获取推荐候选
        List<CandidateItem> candidates = buildCandidates(userId, similarUsers, userLikedContents);

        // 4. 计算推荐分并排序
        List<Long> result = candidates.stream()
                .sorted(Comparator.comparingDouble(CandidateItem::getScore).reversed())
                .limit(limit)
                .map(CandidateItem::getContentId)
                .collect(Collectors.toList());

        long cost = System.currentTimeMillis() - startTime;
        log.info("协同过滤推荐完成: userId={}, 候选数={}, 结果数={}, 耗时={}ms",
                userId, candidates.size(), result.size(), cost);

        // 缓存结果
        cacheResult(userId, result);

        return result;
    }

    /**
     * 获取用户行为向量（该用户点赞过的内容ID集合）
     */
    public Set<Long> getUserBehaviorVector(Long userId) {
        String key = String.format(USER_BEHAVIOR_KEY, userId);
        Set<String> members = redisUtils.sMembers(key);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        return members.stream().map(Long::parseLong).collect(Collectors.toSet());
    }

    /**
     * 计算用户间的相似度，找到 Top-K 相似用户
     */
    public Map<Long, Double> findSimilarUsers(Long targetUserId, Set<Long> targetUserLikes) {
        if (targetUserLikes.isEmpty()) {
            return Collections.emptyMap();
        }

        // 对每个点赞内容，找到也点赞过该内容的其他用户
        Map<Long, Set<Long>> userInteractedContents = new HashMap<>();

        for (Long contentId : targetUserLikes) {
            Set<String> likers = redisUtils.sMembers(String.format(USER_BEHAVIOR_KEY.replace("user:likes:", "content:likes:"), contentId));

            if (likers != null) {
                for (String likerIdStr : likers) {
                    Long likerId = Long.parseLong(likerIdStr);
                    if (!likerId.equals(targetUserId)) {
                        userInteractedContents.computeIfAbsent(likerId, k -> new HashSet<>()).add(contentId);
                    }
                }
            }
        }

        // 计算余弦相似度
        int targetSize = targetUserLikes.size();
        Map<Long, Double> similarityMap = new HashMap<>();

        for (Map.Entry<Long, Set<Long>> entry : userInteractedContents.entrySet()) {
            Long otherUserId = entry.getKey();
            Set<Long> intersection = entry.getValue();
            int otherSize = getUserBehaviorVector(otherUserId).size();

            if (otherSize == 0) continue;

            // 余弦相似度
            double sim = (double) intersection.size() / Math.sqrt(targetSize * otherSize);

            if (sim > 0.01) {
                similarityMap.put(otherUserId, sim);
            }
        }

        // 取 Top-K 相似用户
        return similarityMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(TOP_K_USERS)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * 从相似用户构建推荐候选列表
     */
    private List<CandidateItem> buildCandidates(Long targetUserId,
                                                  Map<Long, Double> similarUsers,
                                                  Set<Long> targetUserLikes) {
        Map<Long, Double> candidateScores = new HashMap<>();

        for (Map.Entry<Long, Double> entry : similarUsers.entrySet()) {
            Long similarUserId = entry.getKey();
            Double similarity = entry.getValue();

            Set<Long> similarUserLikes = getUserBehaviorVector(similarUserId);

            for (Long contentId : similarUserLikes) {
                // 排除目标用户已经点赞过的内容
                if (targetUserLikes.contains(contentId.toString())) {
                    continue;
                }

                // 累加相似度作为推荐分
                candidateScores.merge(contentId, similarity, Double::sum);
            }
        }

        return candidateScores.entrySet().stream()
                .map(e -> new CandidateItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 更新用户行为向量（当用户点赞/取消点赞时调用）
     */
    public void updateUserBehaviorVector(Long userId, Long contentId, boolean isLike) {
        String userKey = String.format(USER_BEHAVIOR_KEY, userId);
        String contentKey = String.format("content:likes:%d", contentId);

        if (isLike) {
            redisUtils.sAdd(userKey, contentId.toString());
            redisUtils.sAdd(contentKey, userId.toString());
        } else {
            redisUtils.sRem(userKey, contentId.toString());
            redisUtils.sRem(contentKey, userId.toString());
        }
    }

    /**
     * 获取用户的协同过滤推荐结果（从缓存）
     */
    public List<Long> getCachedResult(Long userId) {
        String key = String.format(CF_RESULT_KEY, userId);
        Set<String> cached = redisUtils.zReverseRange(key, 0, -1);
        if (cached == null || cached.isEmpty()) {
            return Collections.emptyList();
        }
        return cached.stream().map(Long::parseLong).collect(Collectors.toList());
    }

    private void cacheResult(Long userId, List<Long> contentIds) {
        String key = String.format(CF_RESULT_KEY, userId);
        redisUtils.delete(key);

        Map<String, Double> scores = new HashMap<>();
        for (int i = 0; i < contentIds.size(); i++) {
            scores.put(contentIds.get(i).toString(), (double) (contentIds.size() - i));
        }

        if (!scores.isEmpty()) {
            redisUtils.zAddBatch(key, scores);
            redisUtils.expire(key, 10L, java.util.concurrent.TimeUnit.MINUTES);
        }
    }

    /**
     * 候选内容项
     */
    private static class CandidateItem {
        private final Long contentId;
        private final Double score;

        public CandidateItem(Long contentId, Double score) {
            this.contentId = contentId;
            this.score = score;
        }

        public Long getContentId() {
            return contentId;
        }

        public Double getScore() {
            return score;
        }
    }
}
