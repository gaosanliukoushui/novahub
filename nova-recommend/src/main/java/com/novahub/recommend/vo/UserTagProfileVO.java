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
@Schema(description = "用户画像标签偏好")
public class UserTagProfileVO {

    @Schema(description = "标签ID")
    private Long tagId;

    @Schema(description = "标签名称")
    private String tagName;

    @Schema(description = "偏好权重（0-1）")
    private Double weight;

    @Schema(description = "该标签下点赞的内容数")
    private Integer likeCount;
}
