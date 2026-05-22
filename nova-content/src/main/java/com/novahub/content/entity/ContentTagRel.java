package com.novahub.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("content_tag_rel")
@Schema(description = "内容标签关联实体")
public class ContentTagRel {

    @TableId(type = IdType.AUTO)
    @Schema(description = "关联ID")
    private Long id;

    @TableField("content_id")
    @Schema(description = "内容ID")
    private Long contentId;

    @TableField("tag_id")
    @Schema(description = "标签ID")
    private Long tagId;
}
