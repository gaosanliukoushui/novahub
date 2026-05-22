package com.novahub.common.ratelimit;

import com.novahub.common.utils.RedisUtils;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 滑动窗口限流器。
 * 使用 Redis ZSet 实现，每个请求以时间戳为 score 写入有序集合，
 * 统计 [now - windowSize, now] 区间内的元素数量来判断是否超限。
 * 所有操作通过 Lua 脚本保证原子性。
 */
@Component
public class SlidingWindowRateLimiter {

    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:slide:";

    private static final String SLIDING_WINDOW_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowSize = tonumber(ARGV[2])
            local maxRequests = tonumber(ARGV[3])

            local windowStart = now - windowSize * 1000

            redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

            local currentCount = redis.call('ZCARD', key)

            if currentCount < maxRequests then
                redis.call('ZADD', key, now, now .. ':' .. math.random())
                redis.call('PEXPIRE', key, windowSize * 1000)
                return 1
            else
                return 0
            end
            """;

    private final RedisUtils redisUtils;

    public SlidingWindowRateLimiter(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    private final RedisScript<Long> slidingWindowScript = RedisScript.of(SLIDING_WINDOW_LUA, Long.class);

    /**
     * 尝试获取限流令牌。
     *
     * @param key             限流 key
     * @param windowSizeSeconds 窗口大小（秒）
     * @param maxRequests      窗口内最大请求数
     * @return true 表示通过，false 表示被限流
     */
    public boolean tryAcquire(String key, long windowSizeSeconds, long maxRequests) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + key;
        long nowMillis = System.currentTimeMillis();
        List<String> keys = List.of(redisKey);
        Long result = redisUtils.executeScript(slidingWindowScript, keys,
                String.valueOf(nowMillis),
                String.valueOf(windowSizeSeconds),
                String.valueOf(maxRequests));
        return result != null && result == 1L;
    }

    /**
     * 获取当前窗口内的请求数。
     */
    public long getCurrentCount(String key, long windowSizeSeconds) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + key;
        long windowStart = System.currentTimeMillis() - windowSizeSeconds * 1000L;
        return redisUtils.zCount(redisKey, windowStart, Double.MAX_VALUE);
    }
}
