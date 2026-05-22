package com.novahub.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_tag")
@Schema(description = "内容标签实体")
public class ContentTag {

    @TableId(type = IdType.AUTO)
    @Schema(description = "标签ID")
    private Long id;

    @Schema(description = "标签名")
    private String name;

    @Schema(description = "标签颜色")
    private String color;

    @TableField("use_count")
    @Schema(description = "被使用次数")
    private Integer useCount;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
