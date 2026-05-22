package com.novahub.search.controller;

import com.novahub.common.annotation.NoAuth;
import com.novahub.common.result.Result;
import com.novahub.search.dto.NovaSearchRequest;
import com.novahub.search.service.IndexSyncService;
import com.novahub.search.service.SearchService;
import com.novahub.search.vo.SearchResultPageVO;
import com.novahub.search.vo.SearchResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "搜索管理")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final IndexSyncService indexSyncService;

    @NoAuth
    @Operation(summary = "兼容前端的统一搜索入口")
    @GetMapping
    public Result<SearchResultPageVO<SearchResultVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {

        NovaSearchRequest request = new NovaSearchRequest();
        request.setKeyword(keyword);
        request.setType(type);
        request.setPage(page);
        request.setPageSize(pageSize);
        request.setSort(sort);
        request.setStartTime(startTime);
        request.setEndTime(endTime);

        return Result.ok(searchService.searchContent(request));
    }

    @NoAuth
    @Operation(summary = "内容全文搜索")
    @GetMapping("/content")
    public Result<SearchResultPageVO<SearchResultVO>> searchContent(
            @Parameter(description = "搜索关键词")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "内容类型：1-帖子 2-视频")
            @RequestParam(required = false) Integer type,
            @Parameter(description = "页码")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "排序: relevance/createTime/likeCount/hot")
            @RequestParam(defaultValue = "relevance") String sort,
            @Parameter(description = "开始时间戳")
            @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳")
            @RequestParam(required = false) Long endTime) {

        NovaSearchRequest request = new NovaSearchRequest();
        request.setKeyword(keyword);
        request.setType(type);
        request.setPage(page);
        request.setPageSize(pageSize);
        request.setSort(sort);
        request.setStartTime(startTime);
        request.setEndTime(endTime);

        return Result.ok(searchService.searchContent(request));
    }

    @NoAuth
    @Operation(summary = "用户搜索")
    @GetMapping("/users")
    public Result<SearchResultPageVO<Map>> searchUsers(
            @Parameter(description = "搜索关键词")
            @RequestParam String keyword,
            @Parameter(description = "页码")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(searchService.searchUsers(keyword, page, pageSize));
    }

    @NoAuth
    @Operation(summary = "标签内容搜索")
    @GetMapping("/tags")
    public Result<SearchResultPageVO<SearchResultVO>> searchByTag(
            @Parameter(description = "标签名")
            @RequestParam String tag,
            @Parameter(description = "页码")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(searchService.searchByTag(tag, page, pageSize));
    }

    @NoAuth
    @Operation(summary = "搜索建议（自动补全）")
    @GetMapping("/suggest")
    public Result<List<String>> searchSuggest(
            @Parameter(description = "前缀关键词")
            @RequestParam String q,
            @Parameter(description = "返回数量")
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(searchService.searchSuggest(q, limit));
    }

    @Operation(summary = "管理员：手动触发全量索引重建")
    @PostMapping("/rebuild")
    public Result<Void> rebuildIndex() {
        indexSyncService.buildFullIndex();
        return Result.ok();
    }
}
