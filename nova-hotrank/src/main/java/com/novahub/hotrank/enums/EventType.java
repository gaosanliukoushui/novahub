package com.novahub.hotrank.enums;

public enum EventType {

    LIKE(1, "点赞"),
    UNLIKE(2, "取消点赞"),
    COLLECT(3, "收藏"),
    UNCOLLECT(4, "取消收藏"),
    COMMENT(5, "评论"),
    DELETE_COMMENT(6, "删除评论"),
    VIEW(7, "浏览");

    private final int code;
    private final String description;

    EventType(int code, String description) {
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
