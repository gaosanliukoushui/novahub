package com.novahub.interaction.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "收藏夹VO")
public class FolderVO {

    @Schema(description = "收藏夹ID")
    private Long id;

    @Schema(description = "收藏夹名称")
    private String name;

    @Schema(description = "是否为默认收藏夹")
    private Integer isDefault;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
