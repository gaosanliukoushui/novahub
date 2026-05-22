package com.novahub.recommend.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RecommendType {

    COLLABORATIVE_FILTER("cf", "协同过滤"),
    CONTENT_BASED("cb", "基于内容推荐"),
    HYBRID("hybrid", "混合推荐"),
    HOT("hot", "热门推荐");

    private final String code;
    private final String description;

    public static RecommendType fromCode(String code) {
        for (RecommendType type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return HYBRID;
    }
}
