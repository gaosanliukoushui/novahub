package com.novahub.interaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "点赞请求")
public class LikeRequest {

    @Schema(description = "内容ID")
    @NotNull(message = "内容ID不能为空")
    private Long contentId;
}
