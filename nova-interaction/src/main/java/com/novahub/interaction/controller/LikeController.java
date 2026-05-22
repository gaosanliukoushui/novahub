package com.novahub.interaction.controller;

import com.novahub.common.annotation.Idempotent;
import com.novahub.common.annotation.IdempotentType;
import com.novahub.common.annotation.NoLogin;
import com.novahub.common.annotation.RateLimitBySlideWindow;
import com.novahub.common.result.PageResult;
import com.novahub.common.result.Result;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.interaction.service.ILikeService;
import com.novahub.interaction.vo.LikeUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "点赞接口")
public class LikeController {

    private final ILikeService likeService;

    public LikeController(ILikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping("/contents/{contentId}/like")
    @Idempotent(key = "'idempotent:like:' + #contentId", type = IdempotentType.LOCK, expireSeconds = 10)
    @RateLimitBySlideWindow(key = "'ratelimit:like:' + #contentId", windowSizeSeconds = 60, maxRequests = 30)
    @Operation(summary = "点赞内容")
    public Result<Boolean> like(
            @Parameter(description = "内容ID") @PathVariable Long contentId) {
        Long userId = SecurityUtils.requireUserId();
        boolean result = likeService.like(userId, contentId);
        return Result.ok(result);
    }

    @DeleteMapping("/contents/{contentId}/like")
    @Idempotent(key = "'idempotent:unlike:' + #contentId", type = IdempotentType.LOCK, expireSeconds = 10)
    @Operation(summary = "取消点赞")
    public Result<Boolean> unlike(
            @Parameter(description = "内容ID") @PathVariable Long contentId) {
        Long userId = SecurityUtils.requireUserId();
        boolean result = likeService.unlike(userId, contentId);
        return Result.ok(result);
    }

    @GetMapping("/contents/{contentId}/likes")
    @Operation(summary = "获取内容的点赞用户列表")
    @NoLogin
    public Result<PageResult<LikeUserVO>> getContentLikeUsers(
            @Parameter(description = "内容ID") @PathVariable Long contentId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Long page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Long pageSize) {
        PageResult<LikeUserVO> result = likeService.getContentLikeUsers(page, pageSize, contentId);
        return Result.ok(result);
    }

    @GetMapping("/contents/likes")
    @Operation(summary = "获取我点赞的内容列表")
    public Result<List<Long>> getMyLikedContentIds() {
        Long userId = SecurityUtils.requireUserId();
        List<Long> contentIds = likeService.getLikedContentIds(userId);
        return Result.ok(contentIds);
    }

    @GetMapping("/contents/{contentId}/like-status")
    @Operation(summary = "检查是否已点赞")
    public Result<Boolean> isLiked(
            @Parameter(description = "内容ID") @PathVariable Long contentId) {
        Long userId = SecurityUtils.requireUserId();
        boolean result = likeService.isLiked(userId, contentId);
        return Result.ok(result);
    }

    @GetMapping("/ranks/likes")
    @Operation(summary = "获取点赞排行榜TOP N")
    @NoLogin
    public Result<List<Map<String, Object>>> getLikeRank(
            @Parameter(description = "排行榜数量，默认10") @RequestParam(defaultValue = "10") Integer topN) {
        List<Map<String, Object>> rankList = likeService.getLikeRank(topN);
        return Result.ok(rankList);
    }
}
