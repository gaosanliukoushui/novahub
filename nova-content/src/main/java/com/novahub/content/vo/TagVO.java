package com.novahub.content.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "标签VO")
public class TagVO {

    @Schema(description = "标签ID")
    private Long id;

    @Schema(description = "标签名")
    private String name;

    @Schema(description = "标签颜色")
    private String color;

    @Schema(description = "使用次数")
    private Integer useCount;
}
