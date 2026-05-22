package com.novahub.interaction.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.PageResult;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.RedisUtils;
import com.novahub.content.entity.Content;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.hotrank.service.StatsEventProducer;
import com.novahub.interaction.entity.ContentLike;
import com.novahub.interaction.mapper.ContentLikeMapper;
import com.novahub.interaction.service.ILikeService;
import com.novahub.interaction.vo.LikeUserVO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LikeServiceImpl implements ILikeService {

    private static final String USER_LIKE_KEY_PREFIX = "user:likes:";
    private static final String CONTENT_LIKE_KEY_PREFIX = "content:likes:";
    private static final String LIKE_RANK_KEY = "like:rank:content";
    private static final String CONTENT_LIKE_COUNT_KEY_PREFIX = "content:like:count:";
    private static final long USER_LIKE_TTL_DAYS = 7;
    private static final long CONTENT_LIKE_TTL_DAYS = 7;
    private static final long LIKE_COUNT_TTL_HOURS = 24;

    private final ContentLikeMapper contentLikeMapper;
    private final ContentMapper contentMapper;
    private final RedisUtils redisUtils;
    private final RedisScript<Long> likeScript;
    private final RedisScript<Long> unlikeScript;
    private final StatsEventProducer statsEventProducer;
    private final MeterRegistry meterRegistry;

    public LikeServiceImpl(ContentLikeMapper contentLikeMapper,
                          ContentMapper contentMapper,
                          RedisUtils redisUtils,
                          StatsEventProducer statsEventProducer,
                          MeterRegistry meterRegistry) {
        this.contentLikeMapper = contentLikeMapper;
        this.contentMapper = contentMapper;
        this.redisUtils = redisUtils;
        this.statsEventProducer = statsEventProducer;
        this.meterRegistry = meterRegistry;
        this.likeScript = RedisScript.of(new ClassPathResource("lua/like.lua"), Long.class);
        this.unlikeScript = RedisScript.of(new ClassPathResource("lua/unlike.lua"), Long.class);
    }

    @Override
    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public boolean like(Long userId, Long contentId) {
        String userLikeKey = USER_LIKE_KEY_PREFIX + userId;
        String contentLikeKey = CONTENT_LIKE_KEY_PREFIX + contentId;

        Long result = redisUtils.executeScript(likeScript,
                Arrays.asList(userLikeKey, contentLikeKey),
                userId.toString(), contentId.toString());

        if (result == 1) {
            ContentLike like = new ContentLike();
            like.setUserId(userId);
            like.setContentId(contentId);
            contentLikeMapper.insert(like);

            LambdaUpdateWrapper<Content> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Content::getId, contentId)
                    .setSql("like_count = like_count + 1");
            contentMapper.update(null, updateWrapper);

            String countKey = CONTENT_LIKE_COUNT_KEY_PREFIX + contentId;
            String countStr = redisUtils.get(countKey);
            long currentCount = countStr != null ? Long.parseLong(countStr) : 0;
            redisUtils.set(countKey, String.valueOf(currentCount + 1), LIKE_COUNT_TTL_HOURS, java.util.concurrent.TimeUnit.HOURS);
            redisUtils.zAdd(LIKE_RANK_KEY, contentId.toString(), currentCount + 1);

            Content content = contentMapper.selectById(contentId);
            if (content != null) {
                statsEventProducer.sendLikeEvent(contentId, content.getUserId(), content.getType(), userId);
            }

            log.info("User {} liked content {}", userId, contentId);
            meterRegistry.counter("interaction.like.total").increment();
            return true;
        } else {
            throw new BusinessException(ResultCode.LIKE_ALREADY_EXISTS);
        }
    }

    @Override
    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public boolean unlike(Long userId, Long contentId) {
        String userLikeKey = USER_LIKE_KEY_PREFIX + userId;
        String contentLikeKey = CONTENT_LIKE_KEY_PREFIX + contentId;

        Long result = redisUtils.executeScript(unlikeScript,
                Arrays.asList(userLikeKey, contentLikeKey),
                userId.toString(), contentId.toString());

        if (result == 1) {
            LambdaQueryWrapper<ContentLike> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ContentLike::getUserId, userId)
                    .eq(ContentLike::getContentId, contentId);
            contentLikeMapper.delete(wrapper);

            LambdaUpdateWrapper<Content> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Content::getId, contentId)
                    .setSql("like_count = like_count - 1");
            contentMapper.update(null, updateWrapper);

            String countKey = CONTENT_LIKE_COUNT_KEY_PREFIX + contentId;
            String countStr = redisUtils.get(countKey);
            long currentCount = countStr != null ? Long.parseLong(countStr) : 0;
            long newCount = Math.max(0, currentCount - 1);
            redisUtils.set(countKey, String.valueOf(newCount), LIKE_COUNT_TTL_HOURS, java.util.concurrent.TimeUnit.HOURS);
            redisUtils.zAdd(LIKE_RANK_KEY, contentId.toString(), newCount);

            Content content = contentMapper.selectById(contentId);
            if (content != null) {
                statsEventProducer.sendUnlikeEvent(contentId, content.getUserId(), content.getType(), userId);
            }

            log.info("User {} unliked content {}", userId, contentId);
            meterRegistry.counter("interaction.unlike.total").increment();
            return true;
        } else {
            throw new BusinessException(ResultCode.LIKE_NOT_EXISTS);
        }
    }

    @Override
    @DS("slave")
    public boolean isLiked(Long userId, Long contentId) {
        String userLikeKey = USER_LIKE_KEY_PREFIX + userId;
        Boolean isMember = redisUtils.sIsMember(userLikeKey, contentId.toString());

        if (isMember == null || !isMember) {
            LambdaQueryWrapper<ContentLike> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ContentLike::getUserId, userId)
                    .eq(ContentLike::getContentId, contentId);
            isMember = contentLikeMapper.exists(wrapper);

            if (Boolean.TRUE.equals(isMember)) {
                String contentLikeKey = CONTENT_LIKE_KEY_PREFIX + contentId;
                redisUtils.sAdd(userLikeKey, contentId.toString());
                redisUtils.sAdd(contentLikeKey, userId.toString());
                redisUtils.expire(userLikeKey, USER_LIKE_TTL_DAYS, java.util.concurrent.TimeUnit.DAYS);
                redisUtils.expire(contentLikeKey, CONTENT_LIKE_TTL_DAYS, java.util.concurrent.TimeUnit.DAYS);
            }
        }

        return Boolean.TRUE.equals(isMember);
    }

    @Override
    @DS("slave")
    public List<Long> getLikedContentIds(Long userId) {
        String userLikeKey = USER_LIKE_KEY_PREFIX + userId;

        Set<String> members = redisUtils.sMembers(userLikeKey);

        if (members == null || members.isEmpty()) {
            LambdaQueryWrapper<ContentLike> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ContentLike::getUserId, userId);
            List<ContentLike> likes = contentLikeMapper.selectList(wrapper);

            if (!likes.isEmpty()) {
                List<String> contentIds = likes.stream()
                        .map(like -> like.getContentId().toString())
                        .collect(Collectors.toList());
                redisUtils.sAdd(userLikeKey, contentIds.toArray(new String[0]));

                String contentLikeKeyPrefix = CONTENT_LIKE_KEY_PREFIX;
                for (ContentLike like : likes) {
                    String contentLikeKey = contentLikeKeyPrefix + like.getContentId();
                    redisUtils.sAdd(contentLikeKey, userId.toString());
                }
                redisUtils.expire(userLikeKey, USER_LIKE_TTL_DAYS, java.util.concurrent.TimeUnit.DAYS);

                return likes.stream()
                        .map(ContentLike::getContentId)
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        return members.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    @Override
    @DS("slave")
    public PageResult<LikeUserVO> getContentLikeUsers(Long page, Long pageSize, Long contentId) {
        LambdaQueryWrapper<ContentLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContentLike::getContentId, contentId)
                .orderByDesc(ContentLike::getCreateTime);

        Page<ContentLike> likePage = new Page<>(page, pageSize);
        Page<ContentLike> result = contentLikeMapper.selectPage(likePage, wrapper);

        List<LikeUserVO> voList = result.getRecords().stream()
                .map(like -> {
                    LikeUserVO vo = new LikeUserVO();
                    vo.setUserId(like.getUserId());
                    vo.setCreateTime(like.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), page, pageSize);
    }

    @Override
    public List<Map<String, Object>> getLikeRank(Integer topN) {
        Set<String> topContentIds = redisUtils.zReverseRange(LIKE_RANK_KEY, 0, topN - 1);

        if (topContentIds == null || topContentIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> rankList = new ArrayList<>();
        int rank = 1;
        for (String contentId : topContentIds) {
            Map<String, Object> item = new HashMap<>();
            item.put("contentId", Long.parseLong(contentId));
            item.put("rank", rank++);
            Double score = redisUtils.zScore(LIKE_RANK_KEY, contentId);
            item.put("likeCount", score != null ? score.longValue() : 0);
            rankList.add(item);
        }

        return rankList;
    }
}
