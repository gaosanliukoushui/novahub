package com.novahub.interaction.controller;

import com.novahub.common.annotation.Idempotent;
import com.novahub.common.annotation.IdempotentType;
import com.novahub.common.annotation.NoLogin;
import com.novahub.common.annotation.RateLimitBySlideWindow;
import com.novahub.common.result.Result;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.interaction.dto.CommentQueryRequest;
import com.novahub.interaction.dto.CommentRequest;
import com.novahub.interaction.service.ICommentService;
import com.novahub.interaction.vo.CommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "评论接口")
public class CommentController {

    private final ICommentService commentService;

    public CommentController(ICommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/contents/{contentId}/comments")
    @Idempotent(key = "'idempotent:comment:' + #contentId", type = IdempotentType.LOCK, expireSeconds = 30)
    @RateLimitBySlideWindow(key = "'ratelimit:comment:' + #contentId", windowSizeSeconds = 60, maxRequests = 20)
    @Operation(summary = "添加评论")
    public Result<CommentVO> addComment(
            @Parameter(description = "内容ID") @PathVariable Long contentId,
            @Valid @RequestBody CommentRequest request) {
        Long userId = SecurityUtils.requireUserId();
        CommentVO comment = commentService.addComment(contentId, userId, request.getContent(), request.getParentId());
        return Result.ok(comment);
    }

    @PostMapping("/comments/{commentId}/replies")
    @Idempotent(key = "'idempotent:reply:' + #commentId", type = IdempotentType.LOCK, expireSeconds = 30)
    @Operation(summary = "回复评论")
    public Result<CommentVO> replyComment(
            @Parameter(description = "评论ID") @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request) {
        Long userId = SecurityUtils.requireUserId();
        CommentVO reply = commentService.addComment(null, userId, request.getContent(), commentId);
        return Result.ok(reply);
    }

    @GetMapping("/contents/{contentId}/comments")
    @Operation(summary = "获取评论列表")
    @NoLogin
    public Result<List<CommentVO>> getComments(
            @Parameter(description = "内容ID") @PathVariable Long contentId,
            @Parameter(description = "父评论ID，为空则查询根评论") @RequestParam(required = false) Long parentId,
            @Parameter(description = "游标，最后一条评论的ID") @RequestParam(required = false) Long cursor,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize) {
        CommentQueryRequest query = new CommentQueryRequest();
        query.setContentId(contentId);
        query.setParentId(parentId);
        query.setCursor(cursor);
        query.setPageSize(pageSize);

        List<CommentVO> comments = commentService.getComments(query);
        return Result.ok(comments);
    }

    @GetMapping("/contents/hot-comments")
    @Operation(summary = "获取热门评论")
    @NoLogin
    public Result<List<CommentVO>> getHotComments(
            @Parameter(description = "内容ID") @RequestParam Long contentId,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") Integer limit) {
        List<CommentVO> hotComments = commentService.getHotComments(contentId, limit);
        return Result.ok(hotComments);
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "删除评论")
    public Result<Boolean> deleteComment(
            @Parameter(description = "评论ID") @PathVariable Long commentId) {
        Long userId = SecurityUtils.requireUserId();
        boolean result = commentService.deleteComment(commentId, userId);
        return Result.ok(result);
    }

    @GetMapping("/comments/{commentId}/reply-count")
    @Operation(summary = "获取评论回复数")
    @NoLogin
    public Result<Integer> getReplyCount(
            @Parameter(description = "评论ID") @PathVariable Long commentId) {
        Integer replyCount = commentService.getReplyCount(commentId);
        return Result.ok(replyCount);
    }
}
