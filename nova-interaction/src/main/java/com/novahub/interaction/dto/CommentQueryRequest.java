package com.novahub.interaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "评论查询请求")
public class CommentQueryRequest {

    @Schema(description = "内容ID")
    private Long contentId;

    @Schema(description = "父评论ID，为空则查询根评论")
    private Long parentId;

    @Schema(description = "游标，最后一条评论的ID，用于分页")
    private Long cursor;

    @Schema(description = "每页大小，默认20")
    private Integer pageSize = 20;
}
