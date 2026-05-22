package com.novahub.common.exception;

/**
 * 限流超限异常。
 * 当请求频率超过阈值时抛出，返回 HTTP 429 Too Many Requests。
 */
public class RateLimitExceededException extends BusinessException {

    private static final int RATE_LIMIT_CODE = 429;

    public RateLimitExceededException() {
        super(RATE_LIMIT_CODE, "请求过于频繁，请稍后重试");
    }

    public RateLimitExceededException(String message) {
        super(RATE_LIMIT_CODE, message);
    }
}
