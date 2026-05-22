package com.novahub.interaction.service;

import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.RedisUtils;
import com.novahub.content.entity.Content;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.hotrank.service.StatsEventProducer;
import com.novahub.interaction.entity.ContentLike;
import com.novahub.interaction.mapper.ContentLikeMapper;
import com.novahub.interaction.service.impl.LikeServiceImpl;
import com.novahub.interaction.vo.LikeUserVO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private ContentLikeMapper contentLikeMapper;

    @Mock
    private ContentMapper contentMapper;

    @Mock
    private RedisUtils redisUtils;

    @Mock
    private StatsEventProducer statsEventProducer;

    private MeterRegistry meterRegistry;

    private LikeServiceImpl likeService;

    private RedisScript<Long> likeScript;
    private RedisScript<Long> unlikeScript;

    @BeforeEach
    void setUp() throws Exception {
        likeScript = RedisScript.of(new ClassPathResource("lua/like.lua"), Long.class);
        unlikeScript = RedisScript.of(new ClassPathResource("lua/unlike.lua"), Long.class);
        meterRegistry = new SimpleMeterRegistry();
        likeService = new LikeServiceImpl(
                contentLikeMapper, contentMapper, redisUtils, statsEventProducer, meterRegistry
        );
    }

    @Nested
    @DisplayName("点赞功能测试")
    class LikeTests {

        @Test
        @DisplayName("点赞成功 - 首次点赞")
        void like_success() {
            Long userId = 1L;
            Long contentId = 100L;

            when(redisUtils.executeScript(any(RedisScript.class), anyList(), any(), any()))
                    .thenReturn(1L);

            Content content = new Content();
            content.setId(contentId);
            content.setUserId(10L);
            content.setType(1);

            when(contentMapper.selectById(contentId)).thenReturn(content);
            when(redisUtils.get("content:like:count:" + contentId)).thenReturn(null);

            boolean result = likeService.like(userId, contentId);

            assertTrue(result);
            verify(contentLikeMapper).insert(any(ContentLike.class));
            verify(contentMapper).update(any(), any());
            verify(statsEventProducer).sendLikeEvent(contentId, 10L, 1, userId);
        }

        @Test
        @DisplayName("点赞失败 - 已点赞")
        void like_fail_alreadyLiked() {
            Long userId = 1L;
            Long contentId = 100L;

            when(redisUtils.executeScript(any(RedisScript.class), anyList(), any(), any()))
                    .thenReturn(0L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> likeService.like(userId, contentId));

            assertEquals(ResultCode.LIKE_ALREADY_EXISTS.getCode(), ex.getCode());
            verify(contentLikeMapper, never()).insert(any(ContentLike.class));
        }
    }

    @Nested
    @DisplayName("取消点赞功能测试")
    class UnlikeTests {

        @Test
        @DisplayName("取消点赞成功 - 正常取消")
        void unlike_success() {
            Long userId = 1L;
            Long contentId = 100L;

            when(redisUtils.executeScript(any(RedisScript.class), anyList(), any(), any()))
                    .thenReturn(1L);

            Content content = new Content();
            content.setId(contentId);
            content.setUserId(10L);
            content.setType(1);

            when(contentMapper.selectById(contentId)).thenReturn(content);
            when(redisUtils.get("content:like:count:" + contentId)).thenReturn("5");

            boolean result = likeService.unlike(userId, contentId);

            assertTrue(result);
            verify(contentLikeMapper).delete(any());
            verify(contentMapper).update(any(), any());
            verify(statsEventProducer).sendUnlikeEvent(contentId, 10L, 1, userId);
        }

        @Test
        @DisplayName("取消点赞失败 - 未点赞")
        void unlike_fail_notLiked() {
            Long userId = 1L;
            Long contentId = 100L;

            when(redisUtils.executeScript(any(RedisScript.class), anyList(), any(), any()))
                    .thenReturn(0L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> likeService.unlike(userId, contentId));

            assertEquals(ResultCode.LIKE_NOT_EXISTS.getCode(), ex.getCode());
            verify(contentLikeMapper, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("检查点赞状态测试")
    class IsLikedTests {

        @Test
        @DisplayName("isLiked - Redis命中")
        void isLiked_redisHit() {
            Long userId = 1L;
            Long contentId = 100L;

            when(redisUtils.sIsMember("user:likes:" + userId, "100")).thenReturn(true);

            boolean result = likeService.isLiked(userId, contentId);

            assertTrue(result);
            verify(contentLikeMapper, never()).exists(any());
        }

        @Test
        @DisplayName("isLiked - Redis未命中，数据库命中，同步到Redis")
        void isLiked_dbHit_syncToRedis() {
            Long userId = 1L;
            Long contentId = 100L;

            when(redisUtils.sIsMember("user:likes:" + userId, "100")).thenReturn(false);
            when(contentLikeMapper.exists(any())).thenReturn(true);

            boolean result = likeService.isLiked(userId, contentId);

            assertTrue(result);
            verify(redisUtils).sAdd("user:likes:" + userId, "100");
            verify(redisUtils).sAdd("content:likes:" + contentId, "1");
        }

        @Test
        @DisplayName("isLiked - Redis和数据库都未命中")
        void isLiked_miss() {
            Long userId = 1L;
            Long contentId = 100L;

            when(redisUtils.sIsMember("user:likes:" + userId, "100")).thenReturn(false);
            when(contentLikeMapper.exists(any())).thenReturn(false);

            boolean result = likeService.isLiked(userId, contentId);

            assertFalse(result);
        }

        @Test
        @DisplayName("isLiked - Redis返回null，查数据库")
        void isLiked_redisNull_dbHit() {
            Long userId = 1L;
            Long contentId = 100L;

            when(redisUtils.sIsMember("user:likes:" + userId, "100")).thenReturn(null);
            when(contentLikeMapper.exists(any())).thenReturn(true);

            boolean result = likeService.isLiked(userId, contentId);

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("获取用户点赞列表测试")
    class GetLikedContentIdsTests {

        @Test
        @DisplayName("获取点赞列表 - Redis有数据")
        void getLikedContentIds_redisHit() {
            Long userId = 1L;

            Set<String> cachedIds = new java.util.LinkedHashSet<>(Arrays.asList("100", "200", "300"));
            when(redisUtils.sMembers("user:likes:" + userId)).thenReturn(cachedIds);

            List<Long> result = likeService.getLikedContentIds(userId);

            assertEquals(3, result.size());
            assertTrue(result.contains(100L));
            assertTrue(result.contains(200L));
            assertTrue(result.contains(300L));
            verify(contentLikeMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("获取点赞列表 - Redis为空，从数据库加载")
        void getLikedContentIds_dbLoad() {
            Long userId = 1L;

            when(redisUtils.sMembers("user:likes:" + userId)).thenReturn(Collections.emptySet());

            ContentLike like1 = new ContentLike();
            like1.setContentId(100L);
            ContentLike like2 = new ContentLike();
            like2.setContentId(200L);

            when(contentLikeMapper.selectList(any())).thenReturn(Arrays.asList(like1, like2));

            List<Long> result = likeService.getLikedContentIds(userId);

            assertEquals(2, result.size());
            assertTrue(result.contains(100L));
            assertTrue(result.contains(200L));
            verify(redisUtils).sAdd(eq("user:likes:" + userId), any(String[].class));
        }

        @Test
        @DisplayName("获取点赞列表 - 全部为空")
        void getLikedContentIds_allEmpty() {
            Long userId = 1L;

            when(redisUtils.sMembers("user:likes:" + userId)).thenReturn(Collections.emptySet());
            when(contentLikeMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<Long> result = likeService.getLikedContentIds(userId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("获取内容点赞用户列表测试")
    class GetContentLikeUsersTests {

        @Test
        @DisplayName("获取内容点赞用户 - 有数据")
        void getContentLikeUsers_withData() {
            Long contentId = 100L;
            Long page = 1L;
            Long pageSize = 10L;

            ContentLike like1 = new ContentLike();
            like1.setUserId(1L);
            like1.setContentId(contentId);

            com.baomidou.mybatisplus.extension.plugins.pagination.Page<ContentLike> mockPage =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);
            mockPage.setRecords(Collections.singletonList(like1));
            mockPage.setTotal(1);

            when(contentLikeMapper.selectPage(any(), any())).thenReturn(mockPage);

            var result = likeService.getContentLikeUsers(page, pageSize, contentId);

            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            assertEquals(1L, result.getRecords().get(0).getUserId());
        }

        @Test
        @DisplayName("获取内容点赞用户 - 空结果")
        void getContentLikeUsers_empty() {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<ContentLike> emptyPage =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            emptyPage.setRecords(Collections.emptyList());
            emptyPage.setTotal(0);

            when(contentLikeMapper.selectPage(any(), any())).thenReturn(emptyPage);

            var result = likeService.getContentLikeUsers(1L, 10L, 999L);

            assertNotNull(result);
            assertTrue(result.getRecords().isEmpty());
        }
    }

    @Nested
    @DisplayName("点赞排行榜测试")
    class GetLikeRankTests {

        @Test
        @DisplayName("获取点赞排行 - 有数据")
        void getLikeRank_withData() {
            Integer topN = 10;

            Set<String> topContentIds = new java.util.LinkedHashSet<>(Arrays.asList("1", "2", "3"));
            when(redisUtils.zReverseRange("like:rank:content", 0, 9)).thenReturn(topContentIds);
            when(redisUtils.zScore("like:rank:content", "1")).thenReturn(1000.0);
            when(redisUtils.zScore("like:rank:content", "2")).thenReturn(500.0);
            when(redisUtils.zScore("like:rank:content", "3")).thenReturn(100.0);

            List<Map<String, Object>> result = likeService.getLikeRank(topN);

            assertEquals(3, result.size());
            assertEquals(1L, result.get(0).get("contentId"));
            assertEquals(1, result.get(0).get("rank"));
            assertEquals(1000L, result.get(0).get("likeCount"));
        }

        @Test
        @DisplayName("获取点赞排行 - 无数据")
        void getLikeRank_empty() {
            when(redisUtils.zReverseRange("like:rank:content", 0, 9)).thenReturn(Collections.emptySet());

            List<Map<String, Object>> result = likeService.getLikeRank(10);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("获取点赞排行 - 分数为null")
        void getLikeRank_nullScore() {
            Set<String> topContentIds = new java.util.LinkedHashSet<>(Collections.singletonList("5"));
            when(redisUtils.zReverseRange("like:rank:content", 0, 9)).thenReturn(topContentIds);
            when(redisUtils.zScore("like:rank:content", "5")).thenReturn(null);

            List<Map<String, Object>> result = likeService.getLikeRank(10);

            assertEquals(1, result.size());
            assertEquals(0L, result.get(0).get("likeCount"));
        }
    }
}
