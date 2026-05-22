package com.novahub.feed.controller;

import com.novahub.common.result.Result;
import com.novahub.feed.dto.FeedRequest;
import com.novahub.feed.service.FeedService;
import com.novahub.feed.vo.FeedItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Feed流管理")
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    @Operation(summary = "获取Feed流")
    @GetMapping
    public Result<List<FeedItemVO>> getFeed(
            @Parameter(description = "Feed类型：1-关注流 2-推荐流 3-热门流")
            @RequestParam(defaultValue = "1") int type,
            @Parameter(description = "游标：上一页最后一条的发布时间戳")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "游标：上一页最后一条的ID")
            @RequestParam(required = false) Long lastId,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "内容类型：1-帖子 2-视频")
            @RequestParam(required = false) Integer contentType) {

        FeedRequest request = new FeedRequest();
        request.setType(type);
        request.setCursor(cursor);
        request.setLastId(lastId);
        request.setPageSize(pageSize);
        request.setContentType(contentType);

        List<FeedItemVO> result;
        switch (type) {
            case 2 -> result = feedService.getRecommendFeed(request);
            case 3 -> result = feedService.getHotFeed(request);
            default -> result = feedService.getFollowingFeed(request);
        }
        return Result.ok(result);
    }

    @Operation(summary = "获取关注流")
    @GetMapping("/following")
    public Result<List<FeedItemVO>> getFollowingFeed(@Valid FeedRequest request) {
        return Result.ok(feedService.getFollowingFeed(request));
    }

    @Operation(summary = "获取推荐流")
    @GetMapping("/recommend")
    public Result<List<FeedItemVO>> getRecommendFeed(@Valid FeedRequest request) {
        return Result.ok(feedService.getRecommendFeed(request));
    }

    @Operation(summary = "获取热门流")
    @GetMapping("/hot")
    public Result<List<FeedItemVO>> getHotFeed(@Valid FeedRequest request) {
        return Result.ok(feedService.getHotFeed(request));
    }

    @Operation(summary = "手动刷新推荐流")
    @PostMapping("/recommend/refresh")
    public Result<Void> refreshRecommend() {
        feedService.buildRecommendFeed();
        return Result.ok();
    }
}
