package com.novahub.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sns_follow")
public class SnsFollow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("follow_id")
    private Long followId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
