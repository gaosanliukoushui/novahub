package com.novahub.common.annotation;

import java.lang.annotation.*;

/**
 * 基于令牌桶算法的限流注解。
 * 使用 Redis + Lua 脚本实现，支持平滑限流，允许突发流量。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimitByTokenBucket {

    /**
     * 限流 key，支持 SpEL 表达式。
     * 默认使用方法全限定名。
     */
    String key() default "";

    /**
     * 令牌桶容量，即最大突发请求数。
     * 默认 100。
     */
    long capacity() default 100;

    /**
     * 每次补充的令牌数量。
     * 默认 10。
     */
    long refillTokens() default 10;

    /**
     * 令牌补充周期，单位：秒。
     * 默认 1 秒补充一次。
     */
    long refillDurationSeconds() default 1;
}
