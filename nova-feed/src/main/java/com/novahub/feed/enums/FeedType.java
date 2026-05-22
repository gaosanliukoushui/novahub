package com.novahub.feed.enums;

public enum FeedType {

    FOLLOWING(1, "关注流"),
    RECOMMEND(2, "推荐流"),
    HOT(3, "热门流");

    private final int code;
    private final String description;

    FeedType(int code, String description) {
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
