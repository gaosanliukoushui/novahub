package com.novahub.notify.controller;

import com.novahub.common.annotation.NoAuth;
import com.novahub.common.result.Result;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.notify.entity.Notification;
import com.novahub.notify.service.NotificationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Hidden
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationCompatibilityController {

    private final NotificationService notificationService;

    @NoAuth
    @GetMapping
    public Result<Map<String, Object>> getNotifications(
            @RequestParam(defaultValue = "1") int page,
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
}
