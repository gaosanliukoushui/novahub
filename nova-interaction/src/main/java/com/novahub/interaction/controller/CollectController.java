package com.novahub.interaction.controller;

import com.novahub.common.annotation.Idempotent;
import com.novahub.common.annotation.IdempotentType;
import com.novahub.common.result.Result;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.interaction.dto.CollectRequest;
import com.novahub.interaction.dto.CreateFolderRequest;
import com.novahub.interaction.service.ICollectService;
import com.novahub.interaction.vo.FolderVO;
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
@Tag(name = "收藏接口")
public class CollectController {

    private final ICollectService collectService;

    public CollectController(ICollectService collectService) {
        this.collectService = collectService;
    }

    @PostMapping("/contents/{contentId}/collect")
    @Idempotent(key = "'idempotent:collect:' + #contentId", type = IdempotentType.LOCK, expireSeconds = 10)
    @Operation(summary = "收藏内容")
    public Result<Boolean> collect(
            @Parameter(description = "内容ID") @PathVariable Long contentId,
            @RequestBody(required = false) CollectRequest request) {
        Long userId = SecurityUtils.requireUserId();
        Long folderId = (request != null && request.getFolderId() != null) ? request.getFolderId() : null;
        boolean result = collectService.collect(userId, contentId, folderId);
        return Result.ok(result);
    }

    @DeleteMapping("/contents/{contentId}/collect")
    @Idempotent(key = "'idempotent:uncollect:' + #contentId", type = IdempotentType.LOCK, expireSeconds = 10)
    @Operation(summary = "取消收藏")
    public Result<Boolean> uncollect(
            @Parameter(description = "内容ID") @PathVariable Long contentId) {
        Long userId = SecurityUtils.requireUserId();
        boolean result = collectService.uncollect(userId, contentId);
        return Result.ok(result);
    }

    @GetMapping("/collections")
    @Operation(summary = "获取我的收藏列表")
    public Result<Object> getCollections(
            @Parameter(description = "收藏夹ID") @RequestParam(required = false) Long folderId) {
        Long userId = SecurityUtils.requireUserId();
        Object collections = collectService.getCollections(userId, folderId);
        return Result.ok(collections);
    }

    @GetMapping("/collect-folders")
    @Operation(summary = "获取我的收藏夹列表")
    public Result<List<FolderVO>> getFolders() {
        Long userId = SecurityUtils.requireUserId();
        List<FolderVO> folders = collectService.getFolders(userId);
        return Result.ok(folders);
    }

    @PostMapping("/collect-folders")
    @Operation(summary = "创建收藏夹")
    public Result<FolderVO> createFolder(@Valid @RequestBody CreateFolderRequest request) {
        Long userId = SecurityUtils.requireUserId();
        FolderVO folder = collectService.createFolder(userId, request.getName());
        return Result.ok(folder);
    }

    @GetMapping("/contents/{contentId}/collect-status")
    @Operation(summary = "检查是否已收藏")
    public Result<Boolean> isCollected(
            @Parameter(description = "内容ID") @PathVariable Long contentId) {
        Long userId = SecurityUtils.requireUserId();
        boolean result = collectService.isCollected(userId, contentId);
        return Result.ok(result);
    }
}
