package com.novahub.hotrank.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_stats")
public class ContentStats {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("content_id")
    private Long contentId;

    @TableField("user_id")
    private Long userId;

    private Integer type;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("collect_count")
    private Integer collectCount;

    @TableField("comment_count")
    private Integer commentCount;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("heat_score")
    private Double heatScore;

    @TableField("last_update_time")
    private LocalDateTime lastUpdateTime;

    @TableField("create_time")
    private LocalDateTime createTime;
}
