package com.novahub.common.ratelimit;

import com.novahub.common.utils.RedisUtils;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 令牌桶限流器。
 * 使用 Redis + Lua 脚本实现，支持平滑限流，允许一定程度的突发流量。
 * 每个 key 存储两个字段：lastRefillTime（上次补充时间）和 tokens（当前令牌数）。
 * 原子操作通过 Lua 脚本保证。
 */
@Component
public class TokenBucketRateLimiter {

    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:token:";

    /**
     * 令牌桶 Lua 脚本。
     * KEYS[1]: 限流 key
     * ARGV[1]: 当前时间戳（毫秒）
     * ARGV[2]: 桶容量
     * ARGV[3]: 每次补充令牌数
     * ARGV[4]: 补充周期（毫秒）
     * 返回: 1=通过, 0=拒绝
     */
    private static final String TOKEN_BUCKET_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refillTokens = tonumber(ARGV[3])
            local refillDurationMs = tonumber(ARGV[4])

            local lastRefillTimeStr = redis.call('HGET', key, 'lastRefillTime')
            local tokensStr = redis.call('HGET', key, 'tokens')

            local lastRefillTime = 0
            local tokens = 0

            if lastRefillTimeStr then
                lastRefillTime = tonumber(lastRefillTimeStr)
            end
            if tokensStr then
                tokens = tonumber(tokensStr)
            end

            if tokens >= capacity then
                tokens = capacity
            end

            local elapsed = now - lastRefillTime
            if elapsed > 0 and lastRefillTime > 0 then
                local periods = math.floor(elapsed / refillDurationMs)
                if periods > 0 then
                    tokens = math.min(capacity, tokens + periods * refillTokens)
                    lastRefillTime = lastRefillTime + periods * refillDurationMs
                end
            elseif lastRefillTime == 0 then
                tokens = capacity
                lastRefillTime = now
            end

            local allowed = 0
            if tokens > 0 then
                tokens = tokens - 1
                allowed = 1
            end

            redis.call('HMSET', key, 'lastRefillTime', lastRefillTime, 'tokens', tokens)
            redis.call('PEXPIRE', key, math.max(refillDurationMs * 3, 60000))

            return allowed
            """;

    private final RedisUtils redisUtils;

    public TokenBucketRateLimiter(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    private final RedisScript<Long> tokenBucketScript = RedisScript.of(TOKEN_BUCKET_LUA, Long.class);

    /**
     * 尝试获取令牌。
     *
     * @param key                    限流 key
     * @param capacity               桶容量（最大突发请求数）
     * @param refillTokens           每次补充的令牌数
     * @param refillDurationSeconds  补充周期（秒）
     * @return true 表示通过，false 表示被限流
     */
    public boolean tryAcquire(String key, long capacity, long refillTokens, long refillDurationSeconds) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + key;
        long nowMillis = System.currentTimeMillis();
        long refillDurationMs = refillDurationSeconds * 1000L;
        List<String> keys = Collections.singletonList(redisKey);
        Long result = redisUtils.executeScript(tokenBucketScript, keys,
                String.valueOf(nowMillis),
                String.valueOf(capacity),
                String.valueOf(refillTokens),
                String.valueOf(refillDurationMs));
        return result != null && result == 1L;
    }
}
