package com.novahub.recommend.controller;

import com.novahub.common.result.Result;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.recommend.dto.RecommendRequest;
import com.novahub.recommend.service.IRecommendService;
import com.novahub.recommend.vo.RecommendResponseVO;
import com.novahub.recommend.vo.UserTagProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
@Tag(name = "推荐接口", description = "内容推荐相关接口")
public class RecommendController {

    private final IRecommendService recommendService;

    @GetMapping
    @Operation(summary = "获取推荐内容", description = "获取个性化推荐内容列表")
    public Result<RecommendResponseVO> getRecommendations(
            @ModelAttribute RecommendRequest request) {

        Long userId = SecurityUtils.getUserId();
        if (userId != null && request.getUserId() == null) {
            request.setUserId(userId);
        }

        RecommendResponseVO response = recommendService.getRecommendations(request);
        return Result.success(response);
    }

    @PostMapping("/exposure")
    @Operation(summary = "记录推荐曝光", description = "记录推荐内容的曝光事件")
    public Result<Void> recordExposure(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        Long contentId = Long.valueOf(params.get("contentId").toString());
        int position = Integer.parseInt(params.get("position").toString());
        String recommendWay = (String) params.getOrDefault("recommendWay", "hybrid");

        recommendService.recordExposure(userId, contentId, position, recommendWay);
        return Result.success();
    }

    @PostMapping("/click")
    @Operation(summary = "记录推荐点击", description = "记录推荐内容的点击事件")
    public Result<Void> recordClick(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        Long contentId = Long.valueOf(params.get("contentId").toString());
        String recommendWay = (String) params.getOrDefault("recommendWay", "hybrid");

        recommendService.recordClick(userId, contentId, recommendWay);
        return Result.success();
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新推荐", description = "刷新当前用户的推荐结果")
    public Result<Void> refreshRecommendations() {
        Long userId = SecurityUtils.requireUserId();
        recommendService.refreshUserRecommendations(userId);
        return Result.success();
    }

    @GetMapping("/hot")
    @Operation(summary = "获取热门推荐", description = "获取热门内容推荐列表")
    public Result<RecommendResponseVO> getHotRecommendations(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(defaultValue = "1") Integer pageNum) {

        if (userId == null) {
            userId = SecurityUtils.getUserId();
        }

        RecommendRequest request = new RecommendRequest();
        request.setUserId(userId);
        request.setRecommendType("hot");
        request.setPageSize(pageSize);
        request.setPageNum(pageNum);
        request.setNeedExperiment(false);

        RecommendResponseVO response = recommendService.getRecommendations(request);
        return Result.success(response);
    }
}
