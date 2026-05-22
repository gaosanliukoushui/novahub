package com.novahub.recommend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "推荐结果项")
public class RecommendItemVO {

    @Schema(description = "内容ID")
    private Long contentId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "内容类型：1-帖子 2-视频")
    private Integer contentType;

    @Schema(description = "封面图URL")
    private String coverUrl;

    @Schema(description = "媒体URL")
    private String mediaUrl;

    @Schema(description = "媒体类型")
    private String mediaType;

    @Schema(description = "作者用户ID")
    private Long authorUserId;

    @Schema(description = "作者昵称")
    private String authorNickname;

    @Schema(description = "作者头像")
    private String authorAvatar;

    @Schema(description = "点赞数")
    private Integer likeCount;

    @Schema(description = "评论数")
    private Integer commentCount;

    @Schema(description = "浏览数")
    private Integer viewCount;

    @Schema(description = "发布时间戳（毫秒）")
    private Long publishTimestamp;

    @Schema(description = "推荐分")
    private Double score;

    @Schema(description = "推荐理由")
    private String reason;

    @Schema(description = "推荐方式：cf/cb/hybrid/hot")
    private String recommendWay;

    @Schema(description = "在结果中的位置")
    private Integer position;
}
