package com.novahub.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "搜索请求")
public class NovaSearchRequest {

    @Schema(description = "搜索关键词")
    private String keyword;

    @Schema(description = "内容类型：1-帖子 2-视频")
    @Min(value = 1)
    @Max(value = 2)
    private Integer type;

    @Schema(description = "页码")
    @Min(value = 1)
    private Integer page = 1;

    @Schema(description = "每页大小")
    @Min(value = 1)
    @Max(value = 50)
    private Integer pageSize = 20;

    @Schema(description = "排序方式: relevance/createTime/likeCount/publishTime")
    private String sort = "relevance";

    @Schema(description = "开始时间（时间戳）")
    private Long startTime;

    @Schema(description = "结束时间（时间戳）")
    private Long endTime;
}
