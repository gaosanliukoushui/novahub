package com.novahub.interaction.service;

import com.novahub.interaction.dto.CommentQueryRequest;
import com.novahub.interaction.vo.CommentVO;

import java.util.List;

public interface ICommentService {

    /**
     * 添加评论
     */
    CommentVO addComment(Long contentId, Long userId, String content, Long parentId);

    /**
     * 删除评论
     */
    boolean deleteComment(Long commentId, Long userId);

    /**
     * 获取评论列表（支持游标分页）
     */
    List<CommentVO> getComments(CommentQueryRequest query);

    /**
     * 获取热门评论
     */
    List<CommentVO> getHotComments(Long contentId, Integer limit);

    /**
     * 获取评论回复数
     */
    Integer getReplyCount(Long commentId);
}
