package com.novahub.interaction.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "评论VO")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentVO {

    @Schema(description = "评论ID")
    private Long id;

    @Schema(description = "内容ID")
    private Long contentId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "用户头像")
    private String avatar;

    @Schema(description = "父评论ID")
    private Long parentId;

    @Schema(description = "根评论ID")
    private Long rootId;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "点赞数")
    private Integer likeCount;

    @Schema(description = "回复数")
    private Integer replyCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "子回复列表")
    private List<CommentVO> replies;
}
