package com.novahub.search.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "搜索结果项")
public class SearchResultVO {

    @Schema(description = "内容ID")
    private Long id;

    @Schema(description = "作者ID")
    private Long userId;

    @Schema(description = "作者昵称")
    private String authorNickname;

    @Schema(description = "作者头像")
    private String authorAvatar;

    @Schema(description = "内容类型：1-帖子 2-视频")
    private Integer type;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "标题高亮片段")
    private String titleHighlight;

    @Schema(description = "正文摘要")
    private String content;

    @Schema(description = "正文高亮片段")
    private String contentHighlight;

    @Schema(description = "标签列表")
    private List<String> tags;

    @Schema(description = "标签高亮")
    private List<String> tagHighlights;

    @Schema(description = "封面图")
    private String coverUrl;

    @Schema(description = "点赞数")
    private Integer likeCount;

    @Schema(description = "评论数")
    private Integer commentCount;

    @Schema(description = "浏览数")
    private Integer viewCount;

    @Schema(description = "发布时间")
    private Long publishTime;

    @Schema(description = "相关性分数")
    private Double score;
}
