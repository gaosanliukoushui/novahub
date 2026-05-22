package com.novahub.notify.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novahub.common.annotation.NoAuth;
import com.novahub.common.result.Result;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.notify.entity.Notification;
import com.novahub.notify.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "通知管理")
@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @NoAuth
    @Operation(summary = "通知消息列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> getNotificationList(
            @Parameter(description = "页码")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") int pageSize) {

        Long userId = SecurityUtils.getUserId();
        Page<Notification> pageResult = notificationService.getNotificationList(userId, page, pageSize);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("pageSize", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        return Result.ok(result);
    }

    @NoAuth
    @Operation(summary = "未读消息数")
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        Long userId = SecurityUtils.getUserId();
        return Result.ok(notificationService.getUnreadCount(userId));
    }

    @NoAuth
    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        Long userId = SecurityUtils.getUserId();
        notificationService.markAllRead(userId);
        return Result.ok();
    }

    @NoAuth
    @Operation(summary = "单条标记已读")
    @PostMapping("/read/{id}")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        notificationService.markRead(userId, id);
        return Result.ok();
    }
}
