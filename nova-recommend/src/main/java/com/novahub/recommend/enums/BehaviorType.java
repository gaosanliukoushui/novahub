package com.novahub.recommend.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BehaviorType {

    VIEW("view", "浏览"),
    LIKE("like", "点赞"),
    COLLECT("collect", "收藏"),
    SHARE("share", "分享"),
    COMMENT("comment", "评论");

    private final String code;
    private final String description;

    public static BehaviorType fromCode(String code) {
        for (BehaviorType type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return VIEW;
    }
}
