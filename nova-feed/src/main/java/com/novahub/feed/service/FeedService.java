package com.novahub.feed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.RedisUtils;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.content.client.UserClient;
import com.novahub.content.entity.Content;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.feed.dto.FeedRequest;
import com.novahub.feed.enums.FeedType;
import com.novahub.feed.vo.FeedItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private final RedisUtils redisUtils;
    private final ContentMapper contentMapper;
    private final UserClient userClient;

    private static final String FEED_INBOX_KEY = "feed:inbox:%d";
    private static final String FEED_RECOMMEND_KEY = "feed:recommend:list";
    private static final String FEED_RECOMMEND_CACHE = "feed:recommend:cache:%d";
    private static final String FEED_HOT_CACHE = "feed:hot:cache:%d";
    private static final String FOLLOWER_COUNT_KEY = "user:follower:count:%d";

    private static final int MAX_INBOX_SIZE = 1000;
    private static final int RECOMMEND_FEED_SIZE = 200;
    private static final long BIG_V_THRESHOLD = 10000;

    public void pushToFollowers(Long userId, Long contentId, Integer contentType, LocalDateTime publishTime) {
        Set<Long> followerIds = getFollowerIds(userId);

        if (followerIds.isEmpty()) {
            return;
        }

        long fanCount = followerIds.size();
        if (fanCount > BIG_V_THRESHOLD) {
            log.info("大V用户触发限流: userId={}, fanCount={}", userId, fanCount);
            int sampleCount = (int) Math.min(MAX_INBOX_SIZE, fanCount * 0.1);
            followerIds = randomSample(followerIds, sampleCount);
        }

        long timestamp = publishTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        String scoreKey = contentId + ":" + timestamp;

        for (Long followerId : followerIds) {
            String inboxKey = String.format(FEED_INBOX_KEY, followerId);
            redisUtils.zAdd(inboxKey, scoreKey, -timestamp);
        }

        log.info("Feed推送完成: contentId={}, userId={}, pushedTo={} followers",
                contentId, userId, followerIds.size());
    }

    public void removeFromFollowers(Long userId, Long contentId) {
        Set<Long> followerIds = getFollowerIds(userId);
        if (followerIds.isEmpty()) return;

        String scoreKeyPrefix = contentId + ":";

        for (Long followerId : followerIds) {
            String inboxKey = String.format(FEED_INBOX_KEY, followerId);
            Set<String> members = redisUtils.zRange(inboxKey, 0, -1);
            if (members == null) continue;
            List<String> toRemove = members.stream()
                    .filter(m -> m.startsWith(scoreKeyPrefix))
                    .toList();
            if (!toRemove.isEmpty()) {
                redisUtils.zRem(inboxKey, toRemove.toArray(new String[0]));
            }
        }
    }

    public List<FeedItemVO> getFollowingFeed(FeedRequest request) {
        Long userId = SecurityUtils.requireUserId();
        String inboxKey = String.format(FEED_INBOX_KEY, userId);

        double maxScore = request.getCursor() != null
                ? -request.getCursor()
                : Double.MAX_VALUE;

        Set<String> items = redisUtils.zReverseRangeByScore(inboxKey, -maxScore, Double.MAX_VALUE);
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> itemList = new ArrayList<>(items);
        int limit = Math.min(request.getPageSize() + 1, itemList.size());
        itemList = itemList.subList(0, limit);

        List<Long> contentIds = parseContentIds(itemList);
        if (contentIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Content> contents = loadContents(contentIds, request.getContentType());
        if (contents.isEmpty()) {
            return Collections.emptyList();
        }

        Long lastTimestamp = null;
        List<FeedItemVO> result = new ArrayList<>();
        for (Content content : contents) {
            FeedItemVO vo = buildFeedItem(content, FeedType.FOLLOWING.getCode());
            result.add(vo);
            long ts = content.getCreateTime() != null
                    ? content.getCreateTime().toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
                    : System.currentTimeMillis();
            lastTimestamp = ts;
        }

        enrichInteractionStatus(result);

        if (result.size() > request.getPageSize()) {
            result = result.subList(0, request.getPageSize());
        }

        log.debug("获取关注流: userId={}, count={}", userId, result.size());
        return result;
    }

    public List<FeedItemVO> getRecommendFeed(FeedRequest request) {
        Long userId = SecurityUtils.getUserId();
        String cacheKey = String.format(FEED_RECOMMEND_CACHE, userId != null ? userId : 0);

        String cached = redisUtils.get(cacheKey);
        List<FeedItemVO> cachedList = null;
        if (cached != null) {
            log.debug("推荐流缓存命中: userId={}", userId);
        }

        Set<String> contentIds = redisUtils.zReverseRange(FEED_RECOMMEND_KEY, 0, RECOMMEND_FEED_SIZE - 1);
        if (contentIds == null || contentIds.isEmpty()) {
            return fallbackRecommendFeed(request);
        }

        List<Content> contents = loadContents(
                contentIds.stream().map(Long::parseLong).collect(Collectors.toList()),
                request.getContentType()
        );

        List<FeedItemVO> result = contents.stream()
                .limit(request.getPageSize())
                .map(c -> buildFeedItem(c, FeedType.RECOMMEND.getCode()))
                .collect(Collectors.toList());

        enrichInteractionStatus(result);
        return result;
    }

    public List<FeedItemVO> getHotFeed(FeedRequest request) {
        Long userId = SecurityUtils.getUserId();
        String cacheKey = String.format(FEED_HOT_CACHE, userId != null ? userId : 0);

        Set<String> hotContentIds = redisUtils.zReverseRange("hotrank:list:all", 0, request.getPageSize() + 10);
        if (hotContentIds == null || hotContentIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Content> contents = loadContents(
                hotContentIds.stream().map(Long::parseLong).collect(Collectors.toList()),
                request.getContentType()
        );

        List<FeedItemVO> result = contents.stream()
                .limit(request.getPageSize())
                .map(c -> buildFeedItem(c, FeedType.HOT.getCode()))
                .collect(Collectors.toList());

        enrichInteractionStatus(result);
        return result;
    }

    @Async
    public void buildRecommendFeed() {
        log.info("开始构建推荐流...");

        LambdaQueryWrapper<Content> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Content::getIsDeleted, 0)
                .eq(Content::getStatus, 2)
                .orderByDesc(Content::getLikeCount, Content::getCreateTime)
                .last("LIMIT " + RECOMMEND_FEED_SIZE);

        List<Content> contents = contentMapper.selectList(wrapper);
        if (contents.isEmpty()) return;

        redisUtils.delete(FEED_RECOMMEND_KEY);

        for (Content content : contents) {
            long timestamp = content.getCreateTime() != null
                    ? content.getCreateTime().toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
                    : System.currentTimeMillis();
            double score = content.getLikeCount() != null ? content.getLikeCount() : 0;
            redisUtils.zAdd(FEED_RECOMMEND_KEY, content.getId().toString(), score);
        }

        log.info("推荐流构建完成，共{}条内容", contents.size());
    }

    @Async
    public void refreshAllInbox(Long userId) {
        log.info("刷新用户收件箱: userId={}", userId);
    }

    private List<FeedItemVO> fallbackRecommendFeed(FeedRequest request) {
        LambdaQueryWrapper<Content> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Content::getIsDeleted, 0)
                .eq(Content::getStatus, 2);

        if (request.getContentType() != null) {
            wrapper.eq(Content::getType, request.getContentType());
        }

        wrapper.orderByDesc(Content::getLikeCount, Content::getCreateTime)
                .last("LIMIT " + request.getPageSize());

        List<Content> contents = contentMapper.selectList(wrapper);

        return contents.stream()
                .map(c -> buildFeedItem(c, FeedType.RECOMMEND.getCode()))
                .collect(Collectors.toList());
    }

    private Set<Long> getFollowerIds(Long userId) {
        String key = "user:followers:" + userId;
        Set<String> members = redisUtils.sMembers(key);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        return members.stream().map(Long::parseLong).collect(Collectors.toSet());
    }

    private List<Long> parseContentIds(List<String> items) {
        return items.stream()
                .map(item -> {
                    int colonIdx = item.indexOf(':');
                    return colonIdx > 0 ? Long.parseLong(item.substring(0, colonIdx)) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Content> loadContents(List<Long> contentIds, Integer contentTypeFilter) {
        if (contentIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Content> contents = contentMapper.selectList(
                new LambdaQueryWrapper<Content>()
                        .in(Content::getId, contentIds)
                        .eq(Content::getIsDeleted, 0)
                        .eq(Content::getStatus, 2)
        );

        if (contentTypeFilter != null) {
            contents = contents.stream()
                    .filter(c -> contentTypeFilter.equals(c.getType()))
                    .collect(Collectors.toList());
        }

        Map<Long, Content> map = contents.stream()
                .collect(Collectors.toMap(Content::getId, c -> c));
        List<Content> ordered = new ArrayList<>();
        for (Long id : contentIds) {
            Content c = map.get(id);
            if (c != null) ordered.add(c);
        }
        return ordered;
    }

    private FeedItemVO buildFeedItem(Content content, int feedType) {
        UserClient.UserInfo userInfo = userClient.getUserInfo(content.getUserId());

        long timestamp = content.getCreateTime() != null
                ? content.getCreateTime().toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
                : System.currentTimeMillis();

        String summary = content.getContent() != null
                ? (content.getContent().length() > 200
                        ? content.getContent().substring(0, 200) + "..."
                        : content.getContent())
                : null;

        return FeedItemVO.builder()
                .contentId(content.getId())
                .userId(content.getUserId())
                .authorNickname(userInfo != null ? userInfo.getNickname() : "未知用户")
                .authorAvatar(userInfo != null ? userInfo.getAvatar() : null)
                .contentType(content.getType())
                .title(content.getTitle())
                .summary(summary)
                .coverUrl(content.getCoverUrl())
                .mediaUrl(content.getMediaUrl())
                .mediaType(content.getMediaType())
                .likeCount(content.getLikeCount())
                .commentCount(content.getCommentCount())
                .viewCount(content.getViewCount())
                .publishTimestamp(timestamp)
                .feedType(feedType)
                .isLiked(false)
                .isCollected(false)
                .build();
    }

    private void enrichInteractionStatus(List<FeedItemVO> items) {
        Long currentUserId = SecurityUtils.getUserId();
        if (currentUserId == null) {
            items.forEach(item -> {
                item.setIsLiked(false);
                item.setIsCollected(false);
            });
        }
    }

    private Set<Long> randomSample(Set<Long> set, int sampleSize) {
        List<Long> list = new ArrayList<>(set);
        Collections.shuffle(list);
        return new HashSet<>(list.subList(0, Math.min(sampleSize, list.size())));
    }
}
