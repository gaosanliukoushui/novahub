package com.novahub.hotrank.enums;

public enum RankType {

    ALL(0, "综合榜"),
    POST(1, "帖子榜"),
    VIDEO(2, "视频榜"),
    TRENDING(3, "趋势榜"),
    DAILY(4, "日榜"),
    WEEKLY(5, "周榜");

    private final int code;
    private final String description;

    RankType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
