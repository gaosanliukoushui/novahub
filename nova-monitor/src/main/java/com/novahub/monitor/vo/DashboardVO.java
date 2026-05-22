package com.novahub.monitor.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "数据看板响应")
public class DashboardVO {

    @Schema(description = "日期 yyyyMMdd")
    private String date;

    @Schema(description = "页面浏览量")
    private long pv;

    @Schema(description = "独立访客数")
    private long uv;

    @Schema(description = "日活用户数")
    private long dau;

    @Schema(description = "周活用户数")
    private long wau;

    @Schema(description = "今日新增内容数")
    private long newContentCount;

    @Schema(description = "今日新增用户数")
    private long newUserCount;
}
