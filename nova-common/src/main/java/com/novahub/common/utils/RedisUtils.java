package com.novahub.common.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class RedisUtils {

    private final StringRedisTemplate redisTemplate;

    public RedisUtils(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== String ====================

    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }

    // ==================== Hash ====================

    public void hSet(String key, String field, String value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    public void hSetAll(String key, Map<String, String> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    public String hGet(String key, String field) {
        Object value = redisTemplate.opsForHash().get(key, field);
        return value == null ? null : value.toString();
    }

    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public Boolean hExists(String key, String field) {
        return redisTemplate.opsForHash().hasKey(key, field);
    }

    public Long hDelete(String key, Object... fields) {
        return redisTemplate.opsForHash().delete(key, fields);
    }

    public Long hSize(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    // ==================== Set ====================

    public Long sAdd(String key, String... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    public Long sRem(String key, String... values) {
        return redisTemplate.opsForSet().remove(key, (Object[]) values);
    }

    public Boolean sIsMember(String key, String value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    public Set<String> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public Long sSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    public Set<String> sInter(String... keys) {
        return redisTemplate.opsForSet().intersect(keys[0], Arrays.asList(Arrays.copyOfRange(keys, 1, keys.length)));
    }

    public Set<String> sUnion(String... keys) {
        return redisTemplate.opsForSet().union(keys[0], Arrays.asList(Arrays.copyOfRange(keys, 1, keys.length)));
    }

    // ==================== ZSet（排行榜）====================

    public Boolean zAdd(String key, String member, double score) {
        return redisTemplate.opsForZSet().add(key, member, score);
    }

    public Long zAddBatch(String key, Map<String, Double> scoreMembers) {
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples = scoreMembers.entrySet().stream()
                .map(e -> org.springframework.data.redis.core.ZSetOperations.TypedTuple.of(e.getKey(), e.getValue()))
                .collect(Collectors.toSet());
        return redisTemplate.opsForZSet().add(key, tuples);
    }

    public Long zRem(String key, String... members) {
        return redisTemplate.opsForZSet().remove(key, (Object[]) members);
    }

    public Long zRank(String key, String member) {
        return redisTemplate.opsForZSet().rank(key, member);
    }

    public Long zReverseRank(String key, String member) {
        return redisTemplate.opsForZSet().reverseRank(key, member);
    }

    public Double zScore(String key, String member) {
        return redisTemplate.opsForZSet().score(key, member);
    }

    public Double zIncrScore(String key, String member, double delta) {
        return redisTemplate.opsForZSet().incrementScore(key, member, delta);
    }

    public Set<String> zRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    public Set<String> zReverseRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().reverseRange(key, start, end);
    }

    public Set<String> zRangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().rangeByScore(key, min, max);
    }

    public Set<String> zReverseRangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().reverseRangeByScore(key, min, max);
    }

    public Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> zRangeWithScores(String key, long start, long end) {
        return redisTemplate.opsForZSet().rangeWithScores(key, start, end);
    }

    public Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> zReverseRangeWithScores(String key, long start, long end) {
        return redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
    }

    public Long zSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    public Long zCount(String key, double min, double max) {
        return redisTemplate.opsForZSet().count(key, min, max);
    }

    // ==================== Number (incr/decr) ====================

    public Long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Long incrBy(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    public Long decr(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }

    // ==================== HyperLogLog ====================

    public Long pfAdd(String key, String... values) {
        return redisTemplate.opsForHyperLogLog().add(key, values);
    }

    public Long pfCount(String... keys) {
        return redisTemplate.opsForHyperLogLog().size(keys);
    }

    // ==================== Hash incr ====================

    public Long hincr(String key, String field, long delta) {
        return redisTemplate.opsForHash().increment(key, field, delta);
    }

    // ==================== Lua 脚本执行 ====================

    public <T> T executeScript(RedisScript<T> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, keys, args);
    }

    public <T> T executeScript(RedisScript<T> script, String key, Object... args) {
        return redisTemplate.execute(script, Collections.singletonList(key), args);
    }
}
