package com.novahub.content.controller;

import com.novahub.common.annotation.Idempotent;
import com.novahub.common.annotation.IdempotentType;
import com.novahub.common.annotation.NoLogin;
import com.novahub.common.annotation.RateLimitBySlideWindow;
import com.novahub.common.result.PageResult;
import com.novahub.common.result.Result;
import com.novahub.content.dto.ContentQueryRequest;
import com.novahub.content.dto.PublishContentRequest;
import com.novahub.content.dto.UpdateContentRequest;
import com.novahub.content.service.IContentService;
import com.novahub.content.vo.ContentListVO;
import com.novahub.content.vo.ContentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
@Tag(name = "内容管理", description = "内容发布、查询、更新、删除等接口")
public class ContentController {

    private final IContentService contentService;

    @PostMapping
    @Idempotent(key = "'content:publish'", expireSeconds = 300, type = IdempotentType.LOCK)
    @RateLimitBySlideWindow(key = "'ratelimit:content:publish'", windowSizeSeconds = 60, maxRequests = 20)
    @Operation(summary = "发布内容", description = "发布新内容，status=0保存草稿，status=1提交审核")
    public Result<Long> publish(@Valid @RequestBody PublishContentRequest request) {
        Long contentId;
        if (request.getStatus() != null && request.getStatus() == 0) {
            contentId = contentService.saveDraft(request);
        } else {
            contentId = contentService.publish(request);
        }
        return Result.ok(contentId);
    }

    @GetMapping("/{id}")
    @NoLogin
    @Operation(summary = "获取内容详情", description = "根据ID获取内容详细信息")
    public Result<ContentVO> getById(
            @Parameter(description = "内容ID") @PathVariable Long id) {
        ContentVO content = contentService.getById(id);
        return Result.ok(content);
    }

    @PutMapping("/{id}")
    @Idempotent(key = "'content:update:' + #id", type = IdempotentType.LOCK, expireSeconds = 30)
    @Operation(summary = "更新内容", description = "更新内容信息，仅作者可操作")
    public Result<Void> update(
            @Parameter(description = "内容ID") @PathVariable Long id,
            @Valid @RequestBody UpdateContentRequest request) {
        contentService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除内容", description = "软删除内容，仅作者可操作")
    public Result<Void> delete(
            @Parameter(description = "内容ID") @PathVariable Long id) {
        contentService.delete(id);
        return Result.ok();
    }

    @GetMapping
    @NoLogin
    @Operation(summary = "分页查询内容列表", description = "根据条件分页查询已发布内容")
    public Result<PageResult<ContentListVO>> getPage(ContentQueryRequest request) {
        PageResult<ContentListVO> result = contentService.getPage(request);
        return Result.ok(result);
    }

    @PostMapping("/drafts")
    @Operation(summary = "保存草稿", description = "保存内容为草稿")
    public Result<Long> saveDraft(@Valid @RequestBody PublishContentRequest request) {
        request.setStatus(0);
        Long contentId = contentService.saveDraft(request);
        return Result.ok(contentId);
    }

    @GetMapping("/drafts")
    @Operation(summary = "获取我的草稿列表", description = "获取当前用户的草稿列表")
    public Result<PageResult<ContentListVO>> getDrafts() {
        PageResult<ContentListVO> result = contentService.getDrafts();
        return Result.ok(result);
    }

    @GetMapping("/users/{userId}/contents")
    @NoLogin
    @Operation(summary = "获取用户的发布内容列表", description = "获取指定用户的已发布内容")
    public Result<PageResult<ContentListVO>> getUserContents(
            @Parameter(description = "用户ID") @PathVariable Long userId,
            ContentQueryRequest request) {
        PageResult<ContentListVO> result = contentService.getUserContents(userId, request);
        return Result.ok(result);
    }
}
