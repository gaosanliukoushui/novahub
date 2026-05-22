package com.novahub.monitor.controller;

import com.novahub.common.annotation.NoAuth;
import com.novahub.common.result.Result;
import com.novahub.monitor.service.ContentStatsService;
import com.novahub.monitor.service.DashboardService;
import com.novahub.monitor.service.PvUvService;
import com.novahub.monitor.vo.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "数据监控看板")
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final PvUvService pvUvService;
    private final ContentStatsService contentStatsService;

    @NoAuth
    @Operation(summary = "今日数据看板")
    @GetMapping("/dashboard")
    public Result<DashboardVO> getDashboard() {
        return Result.ok(dashboardService.getTodayDashboard());
    }

    @NoAuth
    @Operation(summary = "指定日期 PV/UV")
    @GetMapping("/pvuv")
    public Result<Map<String, Object>> getPvUv(
            @Parameter(description = "日期 yyyyMMdd")
            @RequestParam String date) {
        return Result.ok(pvUvService.getPvUv(date));
    }

    @NoAuth
    @Operation(summary = "近N天趋势")
    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrend(
            @Parameter(description = "天数")
            @RequestParam(defaultValue = "7") int days) {
        return Result.ok(dashboardService.getTrend(days));
    }

    @NoAuth
    @Operation(summary = "内容 TOP10")
    @GetMapping("/content/top")
    public Result<List<Map<String, Object>>> getTopContent(
            @Parameter(description = "类型：like/comment/view")
            @RequestParam(defaultValue = "like") String type,
            @Parameter(description = "数量")
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(contentStatsService.getTopContent(type, limit));
    }
}
