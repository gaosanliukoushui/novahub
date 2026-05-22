package com.novahub.common.annotation;

import java.lang.annotation.*;

/**
 * 接口幂等性注解。
 * 支持两种模式：
 * - LOCK: 分布式锁式，首次请求加锁，后续请求在锁过期前直接拒绝（快速失败）
 * - TOKEN: Token 验证式，前端传递唯一 token，后端校验并删除，保证只生效一次
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等 key，支持 SpEL 表达式。
     * 默认使用方法全限定名 + 参数摘要。
     */
    String key() default "";

    /**
     * 锁过期时间，单位：秒。
     * 默认 300 秒（5分钟）。
     */
    long expireSeconds() default 300;

    /**
     * 幂等类型。
     * - LOCK: 分布式锁模式，相同 key 快速失败
     * - TOKEN: Token 验证模式，前端传 token，后端查删
     */
    IdempotentType type() default IdempotentType.LOCK;

    /**
     * Token 模式下的请求头名称。
     * 默认 X-Idempotent-Token。
     */
    String tokenHeader() default "X-Idempotent-Token";
}
