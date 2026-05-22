package com.novahub.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "推荐请求")
public class RecommendRequest {

    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Schema(description = "内容类型筛选：1-帖子 2-视频")
    private Integer contentType;

    @Schema(description = "推荐类型：cf/cb/hybrid/hot", example = "hybrid")
    private String recommendType = "hybrid";

    @Schema(description = "每页数量", example = "20")
    @Min(value = 1, message = "pageSize 最小值为 1")
    @Max(value = 100, message = "pageSize 最大值为 100")
    private Integer pageSize = 20;

    @Schema(description = "页码", example = "1")
    @Min(value = 1, message = "pageNum 最小值为 1")
    private Integer pageNum = 1;

    @Schema(description = "是否需要实验信息")
    private Boolean needExperiment = true;

    @Schema(description = "实验ID（可选，指定参与某个实验）")
    private String experimentId;
}
