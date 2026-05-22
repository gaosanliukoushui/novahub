package com.novahub.feed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Feed 项视图对象")
public class FeedItemVO {

    @Schema(description = "内容ID")
    private Long contentId;

    @Schema(description = "作者ID")
    private Long userId;

    @Schema(description = "作者昵称")
    private String authorNickname;

    @Schema(description = "作者头像")
    private String authorAvatar;

    @Schema(description = "内容类型：1-帖子 2-视频")
    private Integer contentType;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "封面图")
    private String coverUrl;

    @Schema(description = "媒体URL")
    private String mediaUrl;

    @Schema(description = "媒体类型")
    private String mediaType;

    @Schema(description = "点赞数")
    private Integer likeCount;

    @Schema(description = "评论数")
    private Integer commentCount;

    @Schema(description = "浏览数")
    private Integer viewCount;

    @Schema(description = "发布时间（用于分页排序）")
    private Long publishTimestamp;

    @Schema(description = "Feed 类型")
    private Integer feedType;

    @Schema(description = "是否已点赞")
    private Boolean isLiked;

    @Schema(description = "是否已收藏")
    private Boolean isCollected;
}
