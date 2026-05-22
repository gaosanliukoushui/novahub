package com.novahub.recommend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ab_bucket")
public class AbBucket {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String experimentId;

    private String bucketId;

    private String bucketName;

    private Double weight;

    private String config;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
