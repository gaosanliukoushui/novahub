package com.novahub.common.annotation;

public enum IdempotentType {

    /**
     * 分布式锁模式。
     * 使用 Redis setIfAbsent 原子加锁，锁过期前重复请求直接拒绝。
     */
    LOCK,

    /**
     * Token 验证模式。
     * 前端传递唯一 token，后端先查询是否存在，再删除，保证只生效一次。
     */
    TOKEN
}
