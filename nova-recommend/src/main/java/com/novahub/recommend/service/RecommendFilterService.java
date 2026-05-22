package com.novahub.recommend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.novahub.common.utils.RedisUtils;
import com.novahub.content.entity.Content;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.recommend.vo.RecommendItemVO;

import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐结果去重与过滤服务
 *
 * 过滤规则：
 * 1. 已读过滤 - 过滤用户已浏览过的内容
 * 2. 已点赞过滤 - 过滤用户已点赞的内容（可选）
 * 3. 已收藏过滤 - 过滤用户已收藏的内容（可选）
 * 4. 作者黑名单 - 过滤用户拉黑的作者
 * 5. 内容状态过滤 - 过滤已删除/下架的内容
 * 6. 时间过滤 - 过滤过旧的内容（可配置）
 * 7. 同一作者去重 - 同一作者内容最多出现 N 次
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendFilterService {

    private final RedisUtils redisUtils;
    private final ContentMapper contentMapper;

    private static final String USER_READ_HISTORY_KEY = "user:read:history:%d";
    private static final String USER_BLOCKLIST_KEY = "user:blocklist:%d";

    private static final int DEFAULT_MAX_SAME_AUTHOR = 2;
    private static final int DEFAULT_MAX_AGE_HOURS = 24 * 30;

    /**
     * 过滤配置
     */
    public record FilterConfig(
            boolean filterRead,
            boolean filterLiked,
            boolean filterCollected,
            boolean filterBlocked,
            boolean filterTime,
            int maxSameAuthor,
            int maxAgeHours
    ) {
        public static FilterConfig defaultConfig() {
            return new FilterConfig(true, false, false, true, true, DEFAULT_MAX_SAME_AUTHOR, DEFAULT_MAX_AGE_HOURS);
        }
    }

    /**
     * 对推荐结果进行过滤
     */
    public List<Long> filter(Long userId, List<Long> contentIds, FilterConfig config) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();

        // 1. 从数据库批量加载内容
        List<Content> contents = loadContents(contentIds);

        // 2. 状态过滤
        contents = contents.stream()
                .filter(c -> c.getStatus() == 2 && c.getIsDeleted() == 0)
                .collect(Collectors.toList());

        if (contents.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> filteredIds = new LinkedHashSet<>();

        // 3. 时间过滤
        if (config.filterTime()) {
            contents = filterByTime(contents, config.maxAgeHours());
        }

        // 4. 获取用户已读内容
        Set<Long> readContentIds = config.filterRead() ? getReadContentIds(userId) : Collections.emptySet();

        // 5. 获取用户黑名单
        Set<Long> blockedUserIds = config.filterBlocked() ? getBlockedUserIds(userId) : Collections.emptySet();

        // 6. 获取用户点赞内容
        Set<Long> likedContentIds = config.filterLiked() ? getLikedContentIds(userId) : Collections.emptySet();

        // 7. 获取用户收藏内容
        Set<Long> collectedContentIds = config.filterCollected() ? getCollectedContentIds(userId) : Collections.emptySet();

        // 8. 按顺序过滤并去重
        Map<Long, Content> contentMap = contents.stream()
                .collect(Collectors.toMap(Content::getId, c -> c, (a, b) -> a));

        Map<Long, Integer> authorAppearCount = new HashMap<>();

        for (Long contentId : contentIds) {
            Content content = contentMap.get(contentId);
            if (content == null) continue;

            // 已读过滤
            if (readContentIds.contains(contentId)) {
                continue;
            }

            // 点赞过滤
            if (likedContentIds.contains(contentId)) {
                continue;
            }

            // 收藏过滤
            if (collectedContentIds.contains(contentId)) {
                continue;
            }

            // 黑名单作者过滤
            if (blockedUserIds.contains(content.getUserId())) {
                continue;
            }

            // 同一作者内容去重
            int authorCount = authorAppearCount.getOrDefault(content.getUserId(), 0);
            if (authorCount >= config.maxSameAuthor()) {
                continue;
            }
            authorAppearCount.merge(content.getUserId(), 1, Integer::sum);

            filteredIds.add(contentId);
        }

        // 记录已读
        if (config.filterRead() && !filteredIds.isEmpty()) {
            recordReadHistory(userId, filteredIds);
        }

        long cost = System.currentTimeMillis() - startTime;
        log.debug("推荐过滤完成: userId={}, 原始数={}, 过滤后={}, 耗时={}ms",
                userId, contentIds.size(), filteredIds.size(), cost);

        return new ArrayList<>(filteredIds);
    }

    /**
     * 批量加载内容
     */
    private List<Content> loadContents(List<Long> contentIds) {
        if (contentIds.isEmpty()) {
            return Collections.emptyList();
        }

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Content> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.in(Content::getId, contentIds);
        return contentMapper.selectList(wrapper);
    }

    /**
     * 时间过滤
     */
    private List<Content> filterByTime(List<Content> contents, int maxAgeHours) {
        long cutoffTime = System.currentTimeMillis() - (long) maxAgeHours * 60 * 60 * 1000;

        return contents.stream()
                .filter(c -> {
                    java.time.LocalDateTime createTime = c.getPublishTime() != null
                            ? c.getPublishTime()
                            : c.getCreateTime();
                    if (createTime == null) return true;
                    return createTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli() > cutoffTime;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取用户已读内容
     */
    public Set<Long> getReadContentIds(Long userId) {
        if (userId == null) return Collections.emptySet();

        String key = String.format(USER_READ_HISTORY_KEY, userId);
        Set<String> members = redisUtils.sMembers(key);

        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }

        return members.stream()
                .filter(m -> !m.contains(":"))
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    /**
     * 记录用户已读内容
     */
    public void recordReadHistory(Long userId, Collection<Long> contentIds) {
        if (userId == null || contentIds == null || contentIds.isEmpty()) {
            return;
        }

        String key = String.format(USER_READ_HISTORY_KEY, userId);
        String[] values = contentIds.stream()
                .map(String::valueOf)
                .toArray(String[]::new);

        redisUtils.sAdd(key, values);
        // 已读列表最多保留 1000 条
        redisUtils.expire(key, 30L, java.util.concurrent.TimeUnit.DAYS);
    }

    /**
     * 获取用户拉黑的作者列表
     */
    public Set<Long> getBlockedUserIds(Long userId) {
        if (userId == null) return Collections.emptySet();

        String key = String.format(USER_BLOCKLIST_KEY, userId);
        Set<String> members = redisUtils.sMembers(key);

        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }

        return members.stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    /**
     * 添加用户黑名单
     */
    public void addToBlocklist(Long userId, Long blockedUserId) {
        if (userId == null || blockedUserId == null) return;

        String key = String.format(USER_BLOCKLIST_KEY, userId);
        redisUtils.sAdd(key, blockedUserId.toString());
    }

    /**
     * 获取用户点赞内容
     */
    private Set<Long> getLikedContentIds(Long userId) {
        if (userId == null) return Collections.emptySet();

        String key = "user:likes:" + userId;
        Set<String> members = redisUtils.sMembers(key);

        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }

        return members.stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    /**
     * 获取用户收藏内容
     */
    private Set<Long> getCollectedContentIds(Long userId) {
        if (userId == null) return Collections.emptySet();

        String key = "user:collects:" + userId;
        Set<String> members = redisUtils.sMembers(key);

        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }

        return members.stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }
}
