package com.novahub.hotrank.controller;

import com.novahub.common.annotation.NoLogin;
import com.novahub.common.result.Result;
import com.novahub.hotrank.enums.RankType;
import com.novahub.hotrank.service.HotRankService;
import com.novahub.hotrank.vo.HotRankVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "热榜管理")
@RestController
@RequestMapping("/api/hotrank")
@RequiredArgsConstructor
public class HotRankController {

    private final HotRankService hotRankService;

    @Operation(summary = "获取热榜列表")
    @GetMapping
    @NoLogin
    public Result<List<HotRankVO>> getHotRank(
            @Parameter(description = "榜单类型: 0-综合 1-帖子 2-视频 3-趋势")
            @RequestParam(defaultValue = "0") int type,
            @Parameter(description = "返回数量")
            @RequestParam(defaultValue = "20") int limit) {
        RankType rankType = RankType.values()[type];
        List<HotRankVO> list = hotRankService.getHotRankList(rankType, limit);
        return Result.ok(list);
    }

    @Operation(summary = "获取综合热榜 TOP N")
    @GetMapping("/all")
    @NoLogin
    public Result<List<HotRankVO>> getAllHotRank(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(hotRankService.getHotRankList(RankType.ALL, limit));
    }

    @Operation(summary = "获取帖子热榜")
    @GetMapping("/posts")
    @NoLogin
    public Result<List<HotRankVO>> getPostHotRank(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(hotRankService.getHotRankList(RankType.POST, limit));
    }

    @Operation(summary = "获取视频热榜")
    @GetMapping("/videos")
    @NoLogin
    public Result<List<HotRankVO>> getVideoHotRank(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(hotRankService.getHotRankList(RankType.VIDEO, limit));
    }

    @Operation(summary = "获取趋势榜")
    @GetMapping("/trending")
    @NoLogin
    public Result<List<HotRankVO>> getTrendingRank(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(hotRankService.getHotRankList(RankType.TRENDING, limit));
    }

    @Operation(summary = "手动触发热榜全量重算")
    @PostMapping("/recalculate")
    public Result<Void> recalculateRank() {
        hotRankService.fullRecalculateRank();
        return Result.ok();
    }
}
