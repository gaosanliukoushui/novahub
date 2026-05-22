package com.novahub.feed.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "Feed 流查询请求")
public class FeedRequest {

    @Schema(description = "Feed 类型：1-关注流 2-推荐流 3-热门流")
    @Min(value = 1, message = "类型最小为1")
    @Max(value = 3, message = "类型最大为3")
    private Integer type = 1;

    @Schema(description = "游标分页：上一页最后一条的发布时间戳")
    private Long cursor;

    @Schema(description = "游标分页：上一页最后一条的ID")
    private Long lastId;

    @Schema(description = "每页大小，默认20")
    @Min(value = 1, message = "每页最小1条")
    @Max(value = 50, message = "每页最大50条")
    private Integer pageSize = 20;

    @Schema(description = "内容类型筛选：1-帖子 2-视频，null表示全部")
    private Integer contentType;
}
