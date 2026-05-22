package com.novahub.recommend.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ExperimentStatus {

    PENDING(0, "未开始"),
    RUNNING(1, "运行中"),
    ENDED(2, "已结束");

    private final int code;
    private final String description;

    public static ExperimentStatus fromCode(int code) {
        for (ExperimentStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return PENDING;
    }
}
