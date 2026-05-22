package com.novahub.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content")
@Schema(description = "内容实体")
public class Content {

    @TableId(type = IdType.AUTO)
    @Schema(description = "内容ID")
    private Long id;

    @Schema(description = "作者ID")
    private Long userId;

    @Schema(description = "类型：1-帖子 2-视频")
    private Integer type;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文")
    private String content;

    @TableField("cover_url")
    @Schema(description = "封面图URL")
    private String coverUrl;

    @TableField("media_url")
    @Schema(description = "媒体URL")
    private String mediaUrl;

    @TableField("media_type")
    @Schema(description = "媒体类型")
    private String mediaType;

    @TableField("status")
    @Schema(description = "发布状态：0-草稿 1-待审核 2-已发布 3-已下架")
    private Integer status;

    @TableField("review_status")
    @Schema(description = "审核状态：0-待审核 1-通过 2-拒绝")
    private Integer reviewStatus;

    @TableField("review_remark")
    @Schema(description = "审核备注")
    private String reviewRemark;

    @TableField("like_count")
    @Schema(description = "点赞数")
    private Integer likeCount;

    @TableField("collect_count")
    @Schema(description = "收藏数")
    private Integer collectCount;

    @TableField("comment_count")
    @Schema(description = "评论数")
    private Integer commentCount;

    @TableField("view_count")
    @Schema(description = "浏览数")
    private Integer viewCount;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    @TableLogic
    @TableField("is_deleted")
    @Schema(description = "软删除标记")
    private Integer isDeleted;
}
