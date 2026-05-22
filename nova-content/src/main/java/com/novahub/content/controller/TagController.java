package com.novahub.content.controller;

import com.novahub.common.annotation.NoLogin;
import com.novahub.common.result.Result;
import com.novahub.content.service.ITagService;
import com.novahub.content.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "标签管理", description = "标签查询相关接口")
public class TagController {

    private final ITagService tagService;

    @GetMapping("/tags")
    @NoLogin
    @Operation(summary = "获取所有标签", description = "获取系统中所有可用标签")
    public Result<List<TagVO>> getAllTags() {
        List<TagVO> tags = tagService.getAllTags();
        return Result.ok(tags);
    }

    @GetMapping("/tags/hot")
    @NoLogin
    @Operation(summary = "获取热门标签", description = "获取使用次数最多的标签")
    public Result<List<TagVO>> getHotTags(
            @Parameter(description = "数量限制") @RequestParam(defaultValue = "10") int limit) {
        List<TagVO> tags = tagService.getHotTags(limit);
        return Result.ok(tags);
    }

    @GetMapping("/contents/{contentId}/tags")
    @NoLogin
    @Operation(summary = "获取内容关联的标签", description = "获取指定内容关联的所有标签")
    public Result<List<TagVO>> getTagsByContentId(
            @Parameter(description = "内容ID") @PathVariable Long contentId) {
        List<TagVO> tags = tagService.getTagsByContentId(contentId);
        return Result.ok(tags);
    }
}
