package com.novahub.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内容查询请求")
public class ContentQueryRequest {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "内容类型：1-帖子 2-视频")
    private Integer type;

    @Schema(description = "发布状态：0-草稿 1-待审核 2-已发布 3-已下架")
    private Integer status;

    @Schema(description = "标签ID")
    private Long tagId;

    @Schema(description = "页码", example = "1")
    private Long page = 1L;

    @Schema(description = "每页大小", example = "10")
    private Long pageSize = 10L;

    @Schema(description = "排序字段：createTime/likeCount")
    private String sortBy = "createTime";
}
