package com.novahub.hotrank.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hot_content_record")
public class HotContentRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("content_id")
    private Long contentId;

    @TableField("content_type")
    private Integer contentType;

    @TableField("rank_type")
    private Integer rankType;

    private Integer rank;

    @TableField("heat_score")
    private Double heatScore;

    @TableField("record_date")
    private LocalDateTime recordDate;

    @TableField("create_time")
    private LocalDateTime createTime;
}
