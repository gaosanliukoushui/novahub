package com.novahub.recommend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_recommend_behavior")
public class UserRecommendBehavior {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long contentId;

    private String behaviorType;

    private String recommendWay;

    private String experimentId;

    private String bucketId;

    private Integer position;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
