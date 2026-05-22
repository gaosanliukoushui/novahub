package com.novahub.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "更新内容请求")
public class UpdateContentRequest {

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
}
