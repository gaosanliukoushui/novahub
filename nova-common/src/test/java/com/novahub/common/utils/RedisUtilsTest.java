package com.novahub.common.utils;

import com.novahub.common.BaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
class RedisUtilsTest extends BaseTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private HyperLogLogOperations<String, String> hllOps;

    private RedisUtils redisUtils;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForHyperLogLog()).thenReturn(hllOps);
        redisUtils = new RedisUtils(redisTemplate);
    }

    @Nested
    @DisplayName("String 操作测试")
    class StringOpsTests {

        @Test
        @DisplayName("set - 无过期时间")
        void setWithoutExpire() {
            redisUtils.set("key1", "value1");

            verify(valueOps).set("key1", "value1");
        }

        @Test
        @DisplayName("set - 带有过期时间")
        void setWithExpire() {
            redisUtils.set("key2", "value2", 30, TimeUnit.MINUTES);

            verify(valueOps).set("key2", "value2", 30, TimeUnit.MINUTES);
        }

        @Test
        @DisplayName("setIfAbsent - 成功设置")
        void setIfAbsent() {
            when(valueOps.setIfAbsent("lock", "1", 10, TimeUnit.SECONDS)).thenReturn(true);

            Boolean result = redisUtils.setIfAbsent("lock", "1", 10, TimeUnit.SECONDS);

            assertTrue(result);
        }

        @Test
        @DisplayName("get - 存在值")
        void getExistingKey() {
            when(valueOps.get("existing")).thenReturn("storedValue");

            String result = redisUtils.get("existing");

            assertEquals("storedValue", result);
        }

        @Test
        @DisplayName("get - 不存在")
        void getNonExistingKey() {
            when(valueOps.get("nonexistent")).thenReturn(null);

            String result = redisUtils.get("nonexistent");

            assertNull(result);
        }

        @Test
        @DisplayName("delete - 单个key")
        void deleteSingleKey() {
            when(redisTemplate.delete("todel")).thenReturn(true);

            Boolean result = redisUtils.delete("todel");

            assertTrue(result);
        }

        @Test
        @DisplayName("delete - 多个key")
        void deleteMultipleKeys() {
            Collection<String> keys = Arrays.asList("k1", "k2", "k3");
            when(redisTemplate.delete(keys)).thenReturn(3L);

            Long result = redisUtils.delete(keys);

            assertEquals(3L, result);
        }

        @Test
        @DisplayName("hasKey - 存在")
        void hasKeyTrue() {
            when(redisTemplate.hasKey("keyexists")).thenReturn(true);

            assertTrue(redisUtils.hasKey("keyexists"));
        }

        @Test
        @DisplayName("hasKey - 不存在")
        void hasKeyFalse() {
            when(redisTemplate.hasKey("keynotexists")).thenReturn(false);

            assertFalse(redisUtils.hasKey("keynotexists"));
        }

        @Test
        @DisplayName("expire - 设置成功")
        void expire() {
            when(redisTemplate.expire("mykey", 60, TimeUnit.SECONDS)).thenReturn(true);

            Boolean result = redisUtils.expire("mykey", 60, TimeUnit.SECONDS);

            assertTrue(result);
        }

        @Test
        @DisplayName("getExpire - 获取过期时间")
        void getExpire() {
            when(redisTemplate.getExpire("ttlkey")).thenReturn(120L);

            Long result = redisUtils.getExpire("ttlkey");

            assertEquals(120L, result);
        }
    }

    @Nested
    @DisplayName("Hash 操作测试")
    class HashOpsTests {

        @Test
        @DisplayName("hSet - 设置单个字段")
        void hSetSingle() {
            redisUtils.hSet("user:1", "name", "Alice");

            verify(hashOps).put("user:1", "name", "Alice");
        }

        @Test
        @DisplayName("hGet - 获取字段值")
        void hGet() {
            when(hashOps.get("user:1", "name")).thenReturn("Alice");

            String result = redisUtils.hGet("user:1", "name");

            assertEquals("Alice", result);
        }

        @Test
        @DisplayName("hGet - 字段不存在")
        void hGetNonExisting() {
            when(hashOps.get("user:1", "nonexistent")).thenReturn(null);

            String result = redisUtils.hGet("user:1", "nonexistent");

            assertNull(result);
        }

        @Test
        @DisplayName("hSetAll - 批量设置")
        void hSetAll() {
            Map<String, String> map = new HashMap<>();
            map.put("name", "Bob");
            map.put("email", "bob@example.com");

            redisUtils.hSetAll("user:2", map);

            verify(hashOps).putAll("user:2", map);
        }

        @Test
        @DisplayName("hGetAll - 获取所有字段")
        void hGetAll() {
            Map<Object, Object> entries = new HashMap<>();
            entries.put("name", "Charlie");
            entries.put("age", "25");
            when(hashOps.entries("user:3")).thenReturn(entries);

            Map<Object, Object> result = redisUtils.hGetAll("user:3");

            assertEquals(2, result.size());
            assertEquals("Charlie", result.get("name"));
        }

        @Test
        @DisplayName("hExists - 字段存在")
        void hExistsTrue() {
            when(hashOps.hasKey("user:1", "field")).thenReturn(true);

            assertTrue(redisUtils.hExists("user:1", "field"));
        }

        @Test
        @DisplayName("hDelete - 删除字段")
        void hDelete() {
            when(hashOps.delete("user:1", "field1", "field2")).thenReturn(2L);

            Long result = redisUtils.hDelete("user:1", "field1", "field2");

            assertEquals(2L, result);
        }

        @Test
        @DisplayName("hSize - 获取字段数量")
        void hSize() {
            when(hashOps.size("user:1")).thenReturn(5L);

            Long result = redisUtils.hSize("user:1");

            assertEquals(5L, result);
        }
    }

    @Nested
    @DisplayName("Set 操作测试")
    class SetOpsTests {

        @Test
        @DisplayName("sAdd - 添加单个成员")
        void sAddSingle() {
            when(setOps.add("myset", "member1")).thenReturn(1L);

            Long result = redisUtils.sAdd("myset", "member1");

            assertEquals(1L, result);
        }

        @Test
        @DisplayName("sAdd - 批量添加")
        void sAddMultiple() {
            when(setOps.add("myset", "a", "b", "c")).thenReturn(3L);

            Long result = redisUtils.sAdd("myset", "a", "b", "c");

            assertEquals(3L, result);
        }

        @Test
        @DisplayName("sRem - 移除成员")
        void sRem() {
            when(setOps.remove("myset", "member1", "member2")).thenReturn(2L);

            Long result = redisUtils.sRem("myset", "member1", "member2");

            assertEquals(2L, result);
        }

        @Test
        @DisplayName("sIsMember - 成员存在")
        void sIsMemberTrue() {
            when(setOps.isMember("myset", "member1")).thenReturn(true);

            assertTrue(redisUtils.sIsMember("myset", "member1"));
        }

        @Test
        @DisplayName("sIsMember - 成员不存在")
        void sIsMemberFalse() {
            when(setOps.isMember("myset", "member99")).thenReturn(false);

            assertFalse(redisUtils.sIsMember("myset", "member99"));
        }

        @Test
        @DisplayName("sMembers - 获取所有成员")
        void sMembers() {
            Set<String> members = new HashSet<>(Arrays.asList("a", "b", "c"));
            when(setOps.members("myset")).thenReturn(members);

            Set<String> result = redisUtils.sMembers("myset");

            assertEquals(3, result.size());
            assertTrue(result.contains("a"));
        }

        @Test
        @DisplayName("sSize - 获取集合大小")
        void sSize() {
            when(setOps.size("myset")).thenReturn(10L);

            Long result = redisUtils.sSize("myset");

            assertEquals(10L, result);
        }
    }

    @Nested
    @DisplayName("ZSet 操作测试")
    class ZSetOpsTests {

        @Test
        @DisplayName("zAdd - 添加成员")
        void zAdd() {
            when(zSetOps.add("leaderboard", "user1", 100.0)).thenReturn(true);

            Boolean result = redisUtils.zAdd("leaderboard", "user1", 100.0);

            assertTrue(result);
        }

        @Test
        @DisplayName("zAddBatch - 批量添加")
        void zAddBatch() {
            Map<String, Double> scoreMembers = new HashMap<>();
            scoreMembers.put("user1", 100.0);
            scoreMembers.put("user2", 200.0);
            when(zSetOps.add(eq("leaderboard"), anySet())).thenReturn(2L);

            Long result = redisUtils.zAddBatch("leaderboard", scoreMembers);

            assertEquals(2L, result);
        }

        @Test
        @DisplayName("zRem - 移除成员")
        void zRem() {
            when(zSetOps.remove("leaderboard", "user1", "user2")).thenReturn(2L);

            Long result = redisUtils.zRem("leaderboard", "user1", "user2");

            assertEquals(2L, result);
        }

        @Test
        @DisplayName("zRank - 获取排名（正序）")
        void zRank() {
            when(zSetOps.rank("leaderboard", "user1")).thenReturn(0L);

            Long result = redisUtils.zRank("leaderboard", "user1");

            assertEquals(0L, result);
        }

        @Test
        @DisplayName("zReverseRank - 获取排名（倒序）")
        void zReverseRank() {
            when(zSetOps.reverseRank("leaderboard", "user1")).thenReturn(5L);

            Long result = redisUtils.zReverseRank("leaderboard", "user1");

            assertEquals(5L, result);
        }

        @Test
        @DisplayName("zScore - 获取分数")
        void zScore() {
            when(zSetOps.score("leaderboard", "user1")).thenReturn(999.5);

            Double result = redisUtils.zScore("leaderboard", "user1");

            assertEquals(999.5, result);
        }

        @Test
        @DisplayName("zIncrScore - 增加分数")
        void zIncrScore() {
            when(zSetOps.incrementScore("leaderboard", "user1", 10.0)).thenReturn(110.0);

            Double result = redisUtils.zIncrScore("leaderboard", "user1", 10.0);

            assertEquals(110.0, result);
        }

        @Test
        @DisplayName("zRange - 正序范围查询")
        void zRange() {
            Set<String> range = new LinkedHashSet<>(Arrays.asList("a", "b", "c"));
            when(zSetOps.range("leaderboard", 0, 2)).thenReturn(range);

            Set<String> result = redisUtils.zRange("leaderboard", 0, 2);

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("zReverseRange - 倒序范围查询")
        void zReverseRange() {
            Set<String> range = new LinkedHashSet<>(Arrays.asList("c", "b", "a"));
            when(zSetOps.reverseRange("leaderboard", 0, 2)).thenReturn(range);

            Set<String> result = redisUtils.zReverseRange("leaderboard", 0, 2);

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("zSize - 获取有序集合大小")
        void zSize() {
            when(zSetOps.size("leaderboard")).thenReturn(50L);

            Long result = redisUtils.zSize("leaderboard");

            assertEquals(50L, result);
        }
    }

    @Nested
    @DisplayName("计数器操作测试")
    class CounterOpsTests {

        @Test
        @DisplayName("incr - 递增")
        void incr() {
            when(valueOps.increment("counter")).thenReturn(1L);

            Long result = redisUtils.incr("counter");

            assertEquals(1L, result);
        }

        @Test
        @DisplayName("incrBy - 指定步长递增")
        void incrBy() {
            when(valueOps.increment("counter", 5L)).thenReturn(10L);

            Long result = redisUtils.incrBy("counter", 5L);

            assertEquals(10L, result);
        }

        @Test
        @DisplayName("decr - 递减")
        void decr() {
            when(valueOps.decrement("counter")).thenReturn(-1L);

            Long result = redisUtils.decr("counter");

            assertEquals(-1L, result);
        }

        @Test
        @DisplayName("hincr - Hash字段递增")
        void hincr() {
            when(hashOps.increment("stats", "views", 1L)).thenReturn(100L);

            Long result = redisUtils.hincr("stats", "views", 1L);

            assertEquals(100L, result);
        }
    }

    @Nested
    @DisplayName("HyperLogLog 操作测试")
    class HyperLogLogTests {

        @Test
        @DisplayName("pfAdd - 添加元素")
        void pfAdd() {
            when(hllOps.add("uv", "user1", "user2")).thenReturn(2L);

            Long result = redisUtils.pfAdd("uv", "user1", "user2");

            assertEquals(2L, result);
        }

        @Test
        @DisplayName("pfCount - 统计基数")
        void pfCount() {
            when(hllOps.size("uv1", "uv2")).thenReturn(1000L);

            Long result = redisUtils.pfCount("uv1", "uv2");

            assertEquals(1000L, result);
        }
    }
}
