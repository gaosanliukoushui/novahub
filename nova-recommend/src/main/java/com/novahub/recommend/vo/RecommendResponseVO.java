package com.novahub.recommend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "推荐结果响应")
public class RecommendResponseVO {

    @Schema(description = "推荐内容列表")
    private List<RecommendItemVO> list;

    @Schema(description = "总数")
    private Long total;

    @Schema(description = "当前页")
    private Integer pageNum;

    @Schema(description = "每页数量")
    private Integer pageSize;

    @Schema(description = "实验信息")
    private ExperimentInfoVO experiment;

    @Schema(description = "请求ID（用于追踪）")
    private String requestId;
}
