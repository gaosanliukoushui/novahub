package com.novahub.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "发布内容请求")
public class PublishContentRequest {

    @NotNull(message = "内容类型不能为空")
    @Min(value = 1, message = "内容类型错误")
    @Max(value = 2, message = "内容类型错误")
    @Schema(description = "类型：1-帖子 2-视频", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer type;

    @Schema(description = "标题")
    @Size(max = 255, message = "标题长度不能超过255")
    private String title;

    @Schema(description = "正文")
    private String content;

    @Schema(description = "封面图URL")
    private String coverUrl;

    @Schema(description = "媒体URL")
    private String mediaUrl;

    @Schema(description = "媒体类型")
    private String mediaType;

    @Schema(description = "标签ID列表")
    private List<Long> tagIds;

    @Schema(description = "状态：0-草稿 1-提交审核", example = "1")
    private Integer status = 0;
}
