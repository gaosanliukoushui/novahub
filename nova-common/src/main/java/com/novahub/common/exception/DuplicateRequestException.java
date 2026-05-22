package com.novahub.common.exception;

/**
 * 重复请求异常。
 * 当接口检测到重复提交时抛出，返回 HTTP 409 Conflict。
 */
public class DuplicateRequestException extends BusinessException {

    private static final int DUPLICATE_CODE = 409;

    public DuplicateRequestException() {
        super(DUPLICATE_CODE, "请勿重复提交，请稍后重试");
    }

    public DuplicateRequestException(String message) {
        super(DUPLICATE_CODE, message);
    }
}
