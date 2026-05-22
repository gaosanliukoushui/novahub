package com.novahub.notify.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_notification")
public class Notification {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long fromUserId;

    private Long toUserId;

    private String type;

    private String content;

    private Long targetId;

    private String targetType;

    private Integer isRead;

    private LocalDateTime readTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    @TableLogic
    private Integer isDeleted;

    public static final String TYPE_LIKE = "LIKE";
    public static final String TYPE_COMMENT = "COMMENT";
    public static final String TYPE_FOLLOW = "FOLLOW";
    public static final String TYPE_MENTION = "MENTION";
    public static final String TYPE_SYSTEM = "SYSTEM";
}
