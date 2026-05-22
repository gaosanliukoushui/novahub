package com.novahub.common.annotation;

import java.lang.annotation.*;

/**
 * 基于滑动窗口算法的限流注解。
 * 使用 Redis ZSet 实现，精确统计固定时间窗口内的请求次数。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimitBySlideWindow {

    /**
     * 限流 key，支持 SpEL 表达式。
     * 例如: "rate:user:#{#userId}" 或 "rate:ip:#{#request.remoteAddr}"
     * 默认使用方法全限定名作为 key 前缀。
     */
    String key() default "";

    /**
     * 窗口大小，单位：秒。
     * 默认 60 秒。
     */
    long windowSizeSeconds() default 60;

    /**
     * 窗口内允许的最大请求数。
     * 默认 100 次。
     */
    long maxRequests() default 100;
}
