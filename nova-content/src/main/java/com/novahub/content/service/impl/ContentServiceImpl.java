package com.novahub.content.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.PageResult;
import com.novahub.common.result.ResultCode;
import com.novahub.common.service.EventOutboxService;
import com.novahub.common.utils.RedisUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.content.client.UserClient;
import com.novahub.content.dto.ContentQueryRequest;
import com.novahub.content.dto.PublishContentRequest;
import com.novahub.content.dto.UpdateContentRequest;
import com.novahub.content.entity.Content;
import com.novahub.content.entity.ContentTagRel;
import com.novahub.content.kafka.ContentEventProducer;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.content.mapper.ContentTagMapper;
import com.novahub.content.mapper.ContentTagRelMapper;
import com.novahub.content.service.IContentService;
import com.novahub.content.service.ITagService;
import com.novahub.content.vo.ContentListVO;
import com.novahub.content.vo.ContentVO;
import com.novahub.content.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentServiceImpl implements IContentService {

    private static final String CONTENT_CACHE_PREFIX = "content:detail:";
    private static final String CONTENT_LOCK_PREFIX = "content:lock:";
    private static final String NULL_MARKER = "NULL_MARKER";
    private static final long CACHE_TTL_MINUTES = 10;
    private static final long NULL_CACHE_TTL_MINUTES = 5;
    private static final long LOCK_TTL_SECONDS = 5;
    private static final int MAX_PUBLISH_PER_USER = 10;
    private static final int PUBLISH_WINDOW_MINUTES = 5;
    private static final String USER_LIKE_KEY_PREFIX = "user:likes:";
    private static final String USER_COLLECT_KEY_PREFIX = "user:collects:";

    private final ContentMapper contentMapper;
    private final ContentTagMapper contentTagMapper;
    private final ContentTagRelMapper contentTagRelMapper;
    private final ITagService tagService;
    private final ContentEventProducer contentEventProducer;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final UserClient userClient;
    private final MeterRegistry meterRegistry;
    private final EventOutboxService eventOutboxService;

    @Override
    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public Long publish(PublishContentRequest request) {
        Long userId = SecurityUtils.requireUserId();

        String rateKey = "ratelimit:publish:" + userId;
        Long count = redisUtils.incr(rateKey);
        if (count == 1) {
            redisUtils.expire(rateKey, PUBLISH_WINDOW_MINUTES, java.util.concurrent.TimeUnit.MINUTES);
        }
        if (count > MAX_PUBLISH_PER_USER) {
            meterRegistry.counter("content.publish.rate_limited", "type", String.valueOf(request.getType())).increment();
            throw new BusinessException(ResultCode.CONTENT_PUBLISH_RATE_LIMITED);
        }
        meterRegistry.counter("content.publish.total", "type", String.valueOf(request.getType())).increment();

        Content content = new Content();
        content.setUserId(userId);
        content.setType(request.getType());
        content.setTitle(request.getTitle());
        content.setContent(request.getContent());
        content.setCoverUrl(request.getCoverUrl());
        content.setMediaUrl(request.getMediaUrl());
        content.setMediaType(request.getMediaType());
        content.setLikeCount(0);
        content.setCollectCount(0);
        content.setCommentCount(0);
        content.setViewCount(0);
        content.setIsDeleted(0);
        content.setCreateTime(LocalDateTime.now());
        content.setUpdateTime(LocalDateTime.now());

        if (request.getStatus() != null && request.getStatus() == 1) {
            content.setStatus(1);
            content.setReviewStatus(0);
        } else {
            content.setStatus(0);
            content.setReviewStatus(1);
        }

        contentMapper.insert(content);
        Long contentId = content.getId();

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            saveTagRelations(contentId, request.getTagIds());
        }

        if (request.getStatus() != null && request.getStatus() == 1) {
            eventOutboxService.record("CONTENT_REVIEW_SUBMITTED", "CONTENT", contentId, "content-publish", request);
            // Kafka event disabled - uncomment when Kafka is available
            // try { contentEventProducer.sendReviewSubmitEvent(contentId, userId); } catch (Exception e) { log.warn("Kafka event failed", e); }
            log.info("内容提交审核: contentId={}, userId={}", contentId, userId);
        }

        return contentId;
    }

    @Override
    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(PublishContentRequest request) {
        Long userId = SecurityUtils.requireUserId();

        Content content = new Content();
        content.setUserId(userId);
        content.setType(request.getType());
        content.setTitle(request.getTitle());
        content.setContent(request.getContent());
        content.setCoverUrl(request.getCoverUrl());
        content.setMediaUrl(request.getMediaUrl());
        content.setMediaType(request.getMediaType());
        content.setStatus(0);
        content.setReviewStatus(1);
        content.setLikeCount(0);
        content.setCollectCount(0);
        content.setCommentCount(0);
        content.setViewCount(0);
        content.setIsDeleted(0);
        content.setCreateTime(LocalDateTime.now());
        content.setUpdateTime(LocalDateTime.now());

        contentMapper.insert(content);
        Long contentId = content.getId();

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            saveTagRelations(contentId, request.getTagIds());
        }

        log.info("保存草稿: contentId={}, userId={}", contentId, userId);
        return contentId;
    }

    @Override
    @DS("slave")
    public ContentVO getById(Long contentId) {
        Timer.Sample sample = Timer.start();
        try {
            String cacheKey = CONTENT_CACHE_PREFIX + contentId;
            String cached = redisUtils.get(cacheKey);

            if (cached != null) {
                if (NULL_MARKER.equals(cached)) {
                    return null;
                }
                try {
                    ContentVO vo = objectMapper.readValue(cached, ContentVO.class);
                    enrichInteractionStatus(vo);
                    return vo;
                } catch (Exception e) {
                    log.warn("解析缓存内容失败: contentId={}", contentId, e);
                }
            }

            String lockKey = CONTENT_LOCK_PREFIX + contentId;
            Boolean acquired = redisUtils.setIfAbsent(lockKey, "1", LOCK_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(acquired)) {
                try {
                    Content content = contentMapper.selectById(contentId);
                    if (content == null || content.getIsDeleted() == 1) {
                        redisUtils.set(cacheKey, NULL_MARKER, NULL_CACHE_TTL_MINUTES, java.util.concurrent.TimeUnit.MINUTES);
                        return null;
                    }

                    ContentVO vo = convertToVO(content);
                    enrichInteractionStatus(vo);

                    try {
                        String json = objectMapper.writeValueAsString(vo);
                        redisUtils.set(cacheKey, json, CACHE_TTL_MINUTES, java.util.concurrent.TimeUnit.MINUTES);
                    } catch (Exception e) {
                        log.warn("缓存内容失败: contentId={}", contentId, e);
                    }

                    incrementViewCountAsync(contentId);
                    return vo;
                } finally {
                    redisUtils.delete(lockKey);
                }
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            String retryCached = redisUtils.get(cacheKey);
            if (retryCached != null && !NULL_MARKER.equals(retryCached)) {
                try {
                    ContentVO vo = objectMapper.readValue(retryCached, ContentVO.class);
                    enrichInteractionStatus(vo);
                    return vo;
                } catch (Exception e) {
                    log.warn("重试解析缓存失败: contentId={}", contentId, e);
                }
            }

            Content content = contentMapper.selectById(contentId);
            if (content == null || content.getIsDeleted() == 1) {
                throw new BusinessException(ResultCode.CONTENT_NOT_FOUND);
            }

            ContentVO vo = convertToVO(content);
            enrichInteractionStatus(vo);
            incrementViewCountAsync(contentId);
            return vo;
        } finally {
            sample.stop(Timer.builder("content.getById.duration").tag("contentId", String.valueOf(contentId)).register(meterRegistry));
        }
    }

    @Override
    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public void update(Long contentId, UpdateContentRequest request) {
        Long userId = SecurityUtils.requireUserId();

        Content content = contentMapper.selectById(contentId);
        if (content == null || content.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.CONTENT_NOT_FOUND);
        }

        if (!content.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.CONTENT_NOT_AUTHOR);
        }

        if (content.getStatus() != null && content.getStatus() == 2) {
            throw new BusinessException(ResultCode.CONTENT_CANNOT_EDIT);
        }

        if (request.getTitle() != null) {
            content.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            content.setContent(request.getContent());
        }
        if (request.getCoverUrl() != null) {
            content.setCoverUrl(request.getCoverUrl());
        }
        if (request.getMediaUrl() != null) {
            content.setMediaUrl(request.getMediaUrl());
        }
        if (request.getMediaType() != null) {
            content.setMediaType(request.getMediaType());
        }

        contentMapper.updateById(content);

        if (request.getTagIds() != null) {
            contentTagRelMapper.deleteByContentId(contentId);
            if (!request.getTagIds().isEmpty()) {
                saveTagRelations(contentId, request.getTagIds());
            }
        }

        String cacheKey = CONTENT_CACHE_PREFIX + contentId;
        redisUtils.delete(cacheKey);

        log.info("更新内容: contentId={}, userId={}", contentId, userId);
    }

    @Override
    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long contentId) {
        Long userId = SecurityUtils.requireUserId();

        Content content = contentMapper.selectById(contentId);
        if (content == null || content.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.CONTENT_NOT_FOUND);
        }

        if (!content.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.CONTENT_NOT_AUTHOR);
        }

        content.setIsDeleted(1);
        contentMapper.updateById(content);

        contentTagRelMapper.deleteByContentId(contentId);

        String cacheKey = CONTENT_CACHE_PREFIX + contentId;
        redisUtils.delete(cacheKey);

        log.info("删除内容: contentId={}, userId={}", contentId, userId);
    }

    @Override
    @DS("slave")
    public PageResult<ContentListVO> getPage(ContentQueryRequest request) {
        Page<Content> page = new Page<>(request.getPage(), request.getPageSize());

        LambdaQueryWrapper<Content> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Content::getIsDeleted, 0);
        wrapper.eq(Content::getStatus, 2);

        if (request.getType() != null) {
            wrapper.eq(Content::getType, request.getType());
        }

        if ("likeCount".equals(request.getSortBy())) {
            wrapper.orderByDesc(Content::getLikeCount, Content::getCreateTime);
        } else {
            wrapper.orderByDesc(Content::getCreateTime);
        }

        IPage<Content> result = contentMapper.selectPage(page, wrapper);

        List<ContentListVO> voList = result.getRecords().stream()
                .map(this::convertToListVO)
                .collect(Collectors.toList());

        enrichListAuthors(voList);
        enrichListTags(voList);
        enrichListInteractionStatus(voList);

        return PageResult.of(voList, result.getTotal(), request.getPage(), request.getPageSize());
    }

    @Override
    @DS("slave")
    public PageResult<ContentListVO> getDrafts() {
        Long userId = SecurityUtils.requireUserId();

        List<Content> drafts = contentMapper.selectDraftsByUserId(userId);

        List<ContentListVO> voList = drafts.stream()
                .map(this::convertToListVO)
                .collect(Collectors.toList());

        enrichListAuthors(voList);
        enrichListTags(voList);
        return PageResult.of(voList, drafts.size(), 1, drafts.size());
    }

    @Override
    @DS("slave")
    public PageResult<ContentListVO> getUserContents(Long userId, ContentQueryRequest request) {
        Page<Content> page = new Page<>(request.getPage(), request.getPageSize());

        LambdaQueryWrapper<Content> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Content::getIsDeleted, 0);
        wrapper.eq(Content::getUserId, userId);
        wrapper.eq(Content::getStatus, 2);

        if (request.getType() != null) {
            wrapper.eq(Content::getType, request.getType());
        }

        if ("likeCount".equals(request.getSortBy())) {
            wrapper.orderByDesc(Content::getLikeCount, Content::getCreateTime);
        } else {
            wrapper.orderByDesc(Content::getCreateTime);
        }

        IPage<Content> result = contentMapper.selectPage(page, wrapper);

        List<ContentListVO> voList = result.getRecords().stream()
                .map(this::convertToListVO)
                .collect(Collectors.toList());

        enrichListAuthors(voList);
        enrichListTags(voList);
        enrichListInteractionStatus(voList);

        return PageResult.of(voList, result.getTotal(), request.getPage(), request.getPageSize());
    }

    @Override
    @Async
    public void incrementViewCount(Long contentId) {
        contentMapper.incrementViewCount(contentId);
        log.debug("增加浏览数: contentId={}", contentId);
    }

    private void incrementViewCountAsync(Long contentId) {
        contentMapper.incrementViewCount(contentId);
    }

    private void saveTagRelations(Long contentId, List<Long> tagIds) {
        List<ContentTagRel> relations = tagIds.stream()
                .map(tagId -> {
                    ContentTagRel rel = new ContentTagRel();
                    rel.setContentId(contentId);
                    rel.setTagId(tagId);
                    return rel;
                })
                .collect(Collectors.toList());

        contentTagRelMapper.batchInsert(relations);

        contentTagMapper.incrementUseCount(tagIds);
    }

    private ContentVO convertToVO(Content content) {
        ContentVO vo = new ContentVO();
        vo.setId(content.getId());
        vo.setUserId(content.getUserId());
        vo.setType(content.getType());
        vo.setTitle(content.getTitle());
        vo.setContent(content.getContent());
        vo.setCoverUrl(content.getCoverUrl());
        vo.setMediaUrl(content.getMediaUrl());
        vo.setMediaType(content.getMediaType());
        vo.setStatus(content.getStatus());
        vo.setReviewStatus(content.getReviewStatus());
        vo.setReviewRemark(content.getReviewRemark());
        vo.setLikeCount(content.getLikeCount());
        vo.setCollectCount(content.getCollectCount());
        vo.setCommentCount(content.getCommentCount());
        vo.setViewCount(content.getViewCount());
        vo.setCreateTime(content.getCreateTime());
        vo.setUpdateTime(content.getUpdateTime());
        vo.setPublishTime(content.getPublishTime());

        UserClient.UserInfo userInfo = userClient.getUserInfo(content.getUserId());
        if (userInfo != null) {
            vo.setAuthorNickname(userInfo.getNickname());
            vo.setAuthorAvatar(userInfo.getAvatar());
        }

        return vo;
    }

    private void enrichListTags(List<ContentListVO> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }

        List<Long> contentIds = voList.stream()
                .map(ContentListVO::getId)
                .collect(Collectors.toList());
        List<ContentTagRel> relations = contentTagRelMapper.selectByContentIds(contentIds);
        if (relations == null || relations.isEmpty()) {
            voList.forEach(vo -> vo.setTags(Collections.emptyList()));
            return;
        }

        Map<Long, List<Long>> contentTagIds = new LinkedHashMap<>();
        List<Long> allTagIds = new ArrayList<>();
        for (ContentTagRel relation : relations) {
            Long contentId = relation.getContentId();
            Long tagId = relation.getTagId();
            if (contentId != null && tagId != null) {
                contentTagIds.computeIfAbsent(contentId, key -> new ArrayList<>()).add(tagId);
                allTagIds.add(tagId);
            }
        }

        Map<Long, TagVO> tagMap = contentTagMapper.selectBatchIds(allTagIds).stream()
                .map(tag -> {
                    TagVO vo = new TagVO();
                    vo.setId(tag.getId());
                    vo.setName(tag.getName());
                    vo.setColor(tag.getColor());
                    vo.setUseCount(tag.getUseCount());
                    return vo;
                })
                .collect(Collectors.toMap(TagVO::getId, tag -> tag, (left, right) -> left));

        for (ContentListVO vo : voList) {
            List<TagVO> tags = contentTagIds.getOrDefault(vo.getId(), Collections.emptyList()).stream()
                    .map(tagMap::get)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            vo.setTags(tags);
        }
    }

    private ContentListVO convertToListVO(Content content) {
        ContentListVO vo = new ContentListVO();
        vo.setId(content.getId());
        vo.setUserId(content.getUserId());
        vo.setType(content.getType());
        vo.setTitle(content.getTitle());
        vo.setCoverUrl(content.getCoverUrl());
        vo.setMediaUrl(content.getMediaUrl());
        vo.setMediaType(content.getMediaType());
        vo.setLikeCount(content.getLikeCount());
        vo.setCollectCount(content.getCollectCount());
        vo.setCommentCount(content.getCommentCount());
        vo.setViewCount(content.getViewCount());
        vo.setCreateTime(content.getCreateTime());

        if (content.getContent() != null) {
            String summary = content.getContent().length() > 200
                    ? content.getContent().substring(0, 200) + "..."
                    : content.getContent();
            vo.setSummary(summary);
        }

        List<TagVO> tags = tagService.getTagsByContentId(content.getId());
        vo.setTags(tags);

        return vo;
    }

    private void enrichListAuthors(List<ContentListVO> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        Map<Long, UserClient.UserInfo> userInfoMap = userClient.getUserInfoMap(
                voList.stream().map(ContentListVO::getUserId).collect(Collectors.toSet()));
        for (ContentListVO vo : voList) {
            UserClient.UserInfo userInfo = userInfoMap.get(vo.getUserId());
            if (userInfo != null) {
                vo.setAuthorNickname(userInfo.getNickname());
                vo.setAuthorAvatar(userInfo.getAvatar());
            }
        }
    }

    private void enrichInteractionStatus(ContentVO vo) {
        if (vo == null) {
            return;
        }
        Long currentUserId = SecurityUtils.getUserId();
        if (currentUserId == null) {
            vo.setIsLiked(false);
            vo.setIsCollected(false);
            return;
        }

        String contentId = String.valueOf(vo.getId());
        vo.setIsLiked(Boolean.TRUE.equals(redisUtils.sIsMember(USER_LIKE_KEY_PREFIX + currentUserId, contentId)));
        vo.setIsCollected(Boolean.TRUE.equals(redisUtils.sIsMember(USER_COLLECT_KEY_PREFIX + currentUserId, contentId)));
    }

    private void enrichListInteractionStatus(List<ContentListVO> voList) {
        Long currentUserId = SecurityUtils.getUserId();
        if (currentUserId == null) {
            voList.forEach(vo -> {
                vo.setIsLiked(false);
                vo.setIsCollected(false);
            });
            return;
        }

        String likeKey = USER_LIKE_KEY_PREFIX + currentUserId;
        String collectKey = USER_COLLECT_KEY_PREFIX + currentUserId;
        for (ContentListVO vo : voList) {
            String contentId = String.valueOf(vo.getId());
            vo.setIsLiked(Boolean.TRUE.equals(redisUtils.sIsMember(likeKey, contentId)));
            vo.setIsCollected(Boolean.TRUE.equals(redisUtils.sIsMember(collectKey, contentId)));
        }
    }
}
