package com.novahub.interaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建收藏夹请求")
public class CreateFolderRequest {

    @Schema(description = "收藏夹名称")
    @NotBlank(message = "收藏夹名称不能为空")
    @Size(max = 50, message = "收藏夹名称不能超过50字")
    private String name;
}
