package com.novahub.recommend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ab_experiment")
public class AbExperiment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String experimentId;

    private String name;

    private String description;

    private Double traffic;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String metrics;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
