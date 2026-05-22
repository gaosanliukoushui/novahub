package com.novahub.hotrank.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "热榜项视图对象")
public class HotRankVO {

    @Schema(description = "内容ID")
    private Long contentId;

    @Schema(description = "作者ID")
    private Long userId;

    @Schema(description = "当前排名")
    private Integer rank;

    @Schema(description = "热度分")
    private Double heatScore;

    @Schema(description = "内容类型：1-帖子 2-视频")
    private Integer contentType;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "封面图")
    private String coverUrl;

    @Schema(description = "作者昵称")
    private String authorNickname;

    @Schema(description = "作者头像")
    private String authorAvatar;

    @Schema(description = "点赞数")
    private Integer likeCount;

    @Schema(description = "收藏数")
    private Integer collectCount;

    @Schema(description = "评论数")
    private Integer commentCount;

    @Schema(description = "浏览数")
    private Integer viewCount;
}
