package com.novahub.interaction.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.RedisUtils;
import com.novahub.content.entity.Content;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.hotrank.service.StatsEventProducer;
import com.novahub.interaction.dto.CommentQueryRequest;
import com.novahub.interaction.entity.ContentComment;
import com.novahub.interaction.mapper.ContentCommentMapper;
import com.novahub.interaction.service.ICommentService;
import com.novahub.interaction.vo.CommentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CommentServiceImpl implements ICommentService {

    private static final String COMMENT_HOT_KEY_PREFIX = "comment:hot:";
    private static final long MAX_TIMESTAMP = 4102444800000L;

    private final ContentCommentMapper contentCommentMapper;
    private final ContentMapper contentMapper;
    private final RedisUtils redisUtils;
    private final StatsEventProducer statsEventProducer;

    public CommentServiceImpl(ContentCommentMapper contentCommentMapper,
                            ContentMapper contentMapper,
                            RedisUtils redisUtils,
                            StatsEventProducer statsEventProducer) {
        this.contentCommentMapper = contentCommentMapper;
        this.contentMapper = contentMapper;
        this.redisUtils = redisUtils;
        this.statsEventProducer = statsEventProducer;
    }

    @Override
    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public CommentVO addComment(Long contentId, Long userId, String content, Long parentId) {
        ContentComment comment = new ContentComment();
        comment.setContentId(contentId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setLikeCount(0);
        comment.setReplyCount(0);
        comment.setStatus(ContentComment.STATUS_NORMAL);

        if (parentId != null) {
            ContentComment parentComment = contentCommentMapper.selectById(parentId);
            if (parentComment == null) {
                throw new BusinessException(ResultCode.COMMENT_NOT_FOUND);
            }
            if (parentComment.getStatus() == ContentComment.STATUS_DELETED) {
                throw new BusinessException(ResultCode.COMMENT_DELETED);
            }

            comment.setParentId(parentId);
            comment.setRootId(parentComment.getRootId() != null ? parentComment.getRootId() : parentId);
            comment.setContentId(parentComment.getContentId());
            contentId = parentComment.getContentId();

            LambdaUpdateWrapper<ContentComment> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ContentComment::getId, parentId)
                    .setSql("reply_count = reply_count + 1");
            contentCommentMapper.update(null, updateWrapper);
        } else {
            comment.setParentId(null);
            comment.setRootId(null);
        }

        contentCommentMapper.insert(comment);

        LambdaUpdateWrapper<Content> contentUpdateWrapper = new LambdaUpdateWrapper<>();
        contentUpdateWrapper.eq(Content::getId, contentId)
                .setSql("comment_count = comment_count + 1");
        contentMapper.update(null, contentUpdateWrapper);

        String hotKey = COMMENT_HOT_KEY_PREFIX + contentId;
        double score = calculateHotScore(comment.getLikeCount(), comment.getCreateTime());
        redisUtils.zAdd(hotKey, comment.getId().toString(), score);

        Content contentEntity = contentMapper.selectById(contentId);
        if (contentEntity != null) {
            statsEventProducer.sendCommentEvent(contentId, contentEntity.getUserId(), contentEntity.getType(), userId);
        }

        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setContentId(comment.getContentId());
        vo.setUserId(comment.getUserId());
        vo.setParentId(comment.getParentId());
        vo.setRootId(comment.getRootId());
        vo.setContent(comment.getContent());
        vo.setLikeCount(comment.getLikeCount());
        vo.setReplyCount(comment.getReplyCount());
        vo.setCreateTime(comment.getCreateTime());

        log.info("User {} added comment {} on content {}", userId, comment.getId(), contentId);
        return vo;
    }

    @Override
    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteComment(Long commentId, Long userId) {
        ContentComment comment = contentCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.COMMENT_NOT_FOUND);
        }

        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限删除该评论");
        }

        if (comment.getStatus() == ContentComment.STATUS_DELETED) {
            throw new BusinessException(ResultCode.COMMENT_DELETED);
        }

        int wasActive = comment.getStatus() == ContentComment.STATUS_NORMAL ? 1 : 0;

        LambdaUpdateWrapper<ContentComment> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ContentComment::getId, commentId)
                .set(ContentComment::getStatus, ContentComment.STATUS_DELETED)
                .set(ContentComment::getContent, "[已删除]")
                .set(ContentComment::getUpdateTime, LocalDateTime.now());
        contentCommentMapper.update(null, updateWrapper);

        if (wasActive == 1) {
            LambdaUpdateWrapper<Content> contentUpdateWrapper = new LambdaUpdateWrapper<>();
            contentUpdateWrapper.eq(Content::getId, comment.getContentId())
                    .setSql("comment_count = comment_count - 1");
            contentMapper.update(null, contentUpdateWrapper);
        }

        if (comment.getParentId() != null) {
            LambdaUpdateWrapper<ContentComment> parentUpdateWrapper = new LambdaUpdateWrapper<>();
            parentUpdateWrapper.eq(ContentComment::getId, comment.getParentId())
                    .setSql("reply_count = reply_count - 1");
            contentCommentMapper.update(null, parentUpdateWrapper);
        }

        log.info("User {} deleted comment {}", userId, commentId);
        return true;
    }

    @Override
    @DS("slave")
    public List<CommentVO> getComments(CommentQueryRequest query) {
        Long contentId = query.getContentId();
        Long parentId = query.getParentId();
        Long cursor = query.getCursor();
        Integer pageSize = query.getPageSize() != null ? query.getPageSize() : 20;

        LambdaQueryWrapper<ContentComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContentComment::getContentId, contentId)
                .eq(ContentComment::getStatus, ContentComment.STATUS_NORMAL);

        if (parentId == null) {
            wrapper.isNull(ContentComment::getParentId);
        } else {
            wrapper.eq(ContentComment::getParentId, parentId);
        }

        if (cursor != null) {
            wrapper.lt(ContentComment::getId, cursor);
        }

        wrapper.orderByDesc(ContentComment::getId)
                .last("LIMIT " + pageSize);

        List<ContentComment> comments = contentCommentMapper.selectList(wrapper);

        return comments.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @DS("slave")
    public List<CommentVO> getHotComments(Long contentId, Integer limit) {
        String hotKey = COMMENT_HOT_KEY_PREFIX + contentId;
        Set<String> hotCommentIds = redisUtils.zReverseRange(hotKey, 0, limit - 1);

        if (hotCommentIds == null || hotCommentIds.isEmpty()) {
            LambdaQueryWrapper<ContentComment> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ContentComment::getContentId, contentId)
                    .eq(ContentComment::getStatus, ContentComment.STATUS_NORMAL)
                    .isNull(ContentComment::getParentId)
                    .orderByDesc(ContentComment::getLikeCount)
                    .orderByDesc(ContentComment::getCreateTime)
                    .last("LIMIT " + limit);

            List<ContentComment> comments = contentCommentMapper.selectList(wrapper);
            return comments.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());
        }

        List<Long> ids = hotCommentIds.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());

        List<ContentComment> comments = contentCommentMapper.selectBatchIds(ids);

        Map<Long, ContentComment> commentMap = comments.stream()
                .collect(Collectors.toMap(ContentComment::getId, c -> c));

        return ids.stream()
                .map(commentMap::get)
                .filter(Objects::nonNull)
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @DS("slave")
    public Integer getReplyCount(Long commentId) {
        ContentComment comment = contentCommentMapper.selectById(commentId);
        if (comment == null) {
            return 0;
        }
        return comment.getReplyCount() != null ? comment.getReplyCount() : 0;
    }

    private CommentVO convertToVO(ContentComment comment) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setContentId(comment.getContentId());
        vo.setUserId(comment.getUserId());
        vo.setParentId(comment.getParentId());
        vo.setRootId(comment.getRootId());
        vo.setContent(comment.getContent());
        vo.setLikeCount(comment.getLikeCount());
        vo.setReplyCount(comment.getReplyCount());
        vo.setCreateTime(comment.getCreateTime());

        if (comment.getReplyCount() != null && comment.getReplyCount() > 0) {
            LambdaQueryWrapper<ContentComment> replyWrapper = new LambdaQueryWrapper<>();
            replyWrapper.eq(ContentComment::getParentId, comment.getId())
                    .eq(ContentComment::getStatus, ContentComment.STATUS_NORMAL)
                    .orderByAsc(ContentComment::getCreateTime)
                    .last("LIMIT 3");

            List<ContentComment> replies = contentCommentMapper.selectList(replyWrapper);
            vo.setReplies(replies.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList()));
        } else {
            vo.setReplies(Collections.emptyList());
        }

        return vo;
    }

    private double calculateHotScore(Integer likeCount, LocalDateTime createTime) {
        long createTimestamp = createTime != null ?
                createTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
                System.currentTimeMillis();
        return likeCount * 10.0 + (MAX_TIMESTAMP - createTimestamp) / 1000000.0;
    }
}
