package com.novahub.recommend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实验信息")
public class ExperimentInfoVO {

    @Schema(description = "实验ID")
    private String experimentId;

    @Schema(description = "桶ID")
    private String bucketId;

    @Schema(description = "实验名称")
    private String experimentName;

    @Schema(description = "桶名称")
    private String bucketName;

    @Schema(description = "请求ID（用于追踪）")
    private String requestId;
}
