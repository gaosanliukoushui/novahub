package com.novahub.user.service;

import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.RedisUtils;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.user.entity.SnsFollow;
import com.novahub.user.entity.SysUser;
import com.novahub.user.mapper.SnsFollowMapper;
import com.novahub.user.mapper.SysUserMapper;
import com.novahub.user.service.impl.FollowServiceImpl;
import com.novahub.user.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private SnsFollowMapper followMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private RedisUtils redisUtils;

    private FollowServiceImpl followService;

    @BeforeEach
    void setUp() {
        followService = new FollowServiceImpl(followMapper, userMapper, redisUtils);
    }

    @AfterEach
    void tearDown() {
        SecurityUtils.clear();
    }

    @Nested
    @DisplayName("关注功能测试")
    class FollowTests {

        @BeforeEach
        void setUserContext() {
            SecurityUtils.setUserId(1L);
        }

        @Test
        @DisplayName("关注成功 - 正常关注")
        void follow_success() {
            Long userId = 1L;
            Long followId = 2L;

            SysUser targetUser = new SysUser();
            targetUser.setId(followId);
            targetUser.setIsDeleted(0);

            when(userMapper.selectById(followId)).thenReturn(targetUser);
            when(followMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
            when(followMapper.insert(any(SnsFollow.class))).thenReturn(1);
            when(userMapper.update(any(), any())).thenReturn(1);

            assertDoesNotThrow(() -> followService.follow(userId, followId));

            verify(followMapper).insert(any(SnsFollow.class));
            verify(userMapper, times(2)).update(any(), any());
            verify(redisUtils).sAdd("user:followings:1", "2");
            verify(redisUtils).sAdd("user:followers:2", "1");
        }

        @Test
        @DisplayName("关注失败 - 不能关注自己")
        void follow_fail_cannotFollowSelf() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> followService.follow(1L, 1L));

            assertEquals(ResultCode.USER_CANNOT_FOLLOW_SELF.getCode(), ex.getCode());
            verify(userMapper, never()).selectById(any());
            verify(followMapper, never()).insert(any(SnsFollow.class));
        }

        @Test
        @DisplayName("关注失败 - 目标用户不存在")
        void follow_fail_targetUserNotFound() {
            when(userMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> followService.follow(1L, 999L));

            assertEquals(ResultCode.USER_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("关注失败 - 已删除用户")
        void follow_fail_targetUserDeleted() {
            SysUser deletedUser = new SysUser();
            deletedUser.setId(2L);
            deletedUser.setIsDeleted(1);

            when(userMapper.selectById(2L)).thenReturn(deletedUser);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> followService.follow(1L, 2L));

            assertEquals(ResultCode.USER_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("关注失败 - 已关注")
        void follow_fail_alreadyFollowed() {
            SysUser targetUser = new SysUser();
            targetUser.setId(2L);
            targetUser.setIsDeleted(0);

            when(userMapper.selectById(2L)).thenReturn(targetUser);
            when(followMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> followService.follow(1L, 2L));

            assertEquals(ResultCode.USER_ALREADY_FOLLOWED.getCode(), ex.getCode());
            verify(followMapper, never()).insert(any(SnsFollow.class));
        }
    }

    @Nested
    @DisplayName("取消关注功能测试")
    class UnfollowTests {

        @BeforeEach
        void setUserContext() {
            SecurityUtils.setUserId(1L);
        }

        @Test
        @DisplayName("取消关注成功 - 正常取关")
        void unfollow_success() {
            Long userId = 1L;
            Long followId = 2L;

            when(followMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
            when(userMapper.update(any(), any())).thenReturn(1);

            assertDoesNotThrow(() -> followService.unfollow(userId, followId));

            verify(followMapper).delete(any(LambdaQueryWrapper.class));
            verify(userMapper, times(2)).update(any(), any());
            verify(redisUtils).sRem("user:followings:1", "2");
            verify(redisUtils).sRem("user:followers:2", "1");
        }

        @Test
        @DisplayName("取消关注失败 - 未关注")
        void unfollow_fail_notFollowing() {
            when(followMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> followService.unfollow(1L, 2L));

            assertEquals(ResultCode.USER_NOT_FOLLOWED.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("获取粉丝列表测试")
    class GetFollowersTests {

        @BeforeEach
        void setUserContext() {
            SecurityUtils.setUserId(1L);
        }

        @Test
        @DisplayName("获取粉丝列表 - 有数据")
        void getFollowers_withData() {
            Long userId = 2L;

            SnsFollow follow1 = new SnsFollow();
            follow1.setUserId(1L);
            follow1.setFollowId(userId);

            SnsFollow follow2 = new SnsFollow();
            follow2.setUserId(3L);
            follow2.setFollowId(userId);

            Page<SnsFollow> followPage = new Page<>(1, 10);
            followPage.setRecords(Arrays.asList(follow1, follow2));
            followPage.setTotal(2);

            SysUser user1 = new SysUser();
            user1.setId(1L);
            user1.setUsername("user1");
            user1.setNickname("User One");
            user1.setFollowCount(5);
            user1.setFansCount(10);
            user1.setWorksCount(3);
            user1.setIsDeleted(0);

            SysUser user3 = new SysUser();
            user3.setId(3L);
            user3.setUsername("user3");
            user3.setNickname("User Three");
            user3.setFollowCount(2);
            user3.setFansCount(8);
            user3.setWorksCount(1);
            user3.setIsDeleted(0);

            when(followMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(followPage);
            when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(user1, user3));
            when(followMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            IPage<UserVO> result = followService.getFollowers(userId, 1, 10);

            assertNotNull(result);
            assertEquals(2, result.getRecords().size());
            assertEquals(2, result.getTotal());
        }

        @Test
        @DisplayName("获取粉丝列表 - 空数据")
        void getFollowers_empty() {
            Page<SnsFollow> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(Collections.emptyList());
            emptyPage.setTotal(0);

            when(followMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

            IPage<UserVO> result = followService.getFollowers(1L, 1, 10);

            assertNotNull(result);
            assertTrue(result.getRecords().isEmpty());
            assertEquals(0, result.getTotal());
        }
    }

    @Nested
    @DisplayName("获取关注列表测试")
    class GetFollowingsTests {

        @BeforeEach
        void setUserContext() {
            SecurityUtils.setUserId(1L);
        }

        @Test
        @DisplayName("获取关注列表 - 有数据")
        void getFollowings_withData() {
            Long userId = 1L;

            SnsFollow follow1 = new SnsFollow();
            follow1.setUserId(userId);
            follow1.setFollowId(2L);

            Page<SnsFollow> followPage = new Page<>(1, 10);
            followPage.setRecords(Collections.singletonList(follow1));
            followPage.setTotal(1);

            SysUser followedUser = new SysUser();
            followedUser.setId(2L);
            followedUser.setUsername("followed");
            followedUser.setNickname("Followed User");
            followedUser.setFollowCount(10);
            followedUser.setFansCount(20);
            followedUser.setWorksCount(5);
            followedUser.setIsDeleted(0);

            when(followMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(followPage);
            when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(followedUser));
            when(followMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            IPage<UserVO> result = followService.getFollowings(userId, 1, 10);

            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            assertEquals("Followed User", result.getRecords().get(0).getNickname());
        }
    }

    @Nested
    @DisplayName("isFollowing 测试")
    class IsFollowingTests {

        @Test
        @DisplayName("isFollowing - 缓存命中")
        void isFollowing_cacheHit() {
            when(redisUtils.sIsMember("user:followings:1", "2")).thenReturn(true);

            assertTrue(followService.isFollowing(1L, 2L));
            verify(followMapper, never()).exists(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("isFollowing - 缓存未命中，查数据库")
        void isFollowing_cacheMiss_dbHit() {
            when(redisUtils.sIsMember("user:followings:1", "2")).thenReturn(false);
            when(followMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

            assertTrue(followService.isFollowing(1L, 2L));

            verify(followMapper).exists(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("isFollowing - 缓存未命中，数据库也没有")
        void isFollowing_cacheMiss_dbMiss() {
            when(redisUtils.sIsMember("user:followings:1", "2")).thenReturn(false);
            when(followMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);

            assertFalse(followService.isFollowing(1L, 2L));
        }
    }

    @Nested
    @DisplayName("获取关注者/粉丝ID列表测试")
    class GetFollowerIdsTests {

        @Test
        @DisplayName("getFollowerIds - Redis有数据")
        void getFollowerIds_cacheHit() {
            Set<String> cachedIds = new HashSet<>(Arrays.asList("10", "20", "30"));
            when(redisUtils.sMembers("user:followers:5")).thenReturn(cachedIds);

            Set<Long> result = followService.getFollowerIds(5L);

            assertEquals(3, result.size());
            assertTrue(result.contains(10L));
            assertTrue(result.contains(20L));
            assertTrue(result.contains(30L));
            verify(followMapper, never()).selectList(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("getFollowerIds - Redis无数据，从DB加载")
        void getFollowerIds_cacheMiss_loadFromDb() {
            when(redisUtils.sMembers("user:followers:5")).thenReturn(Collections.emptySet());

            SnsFollow follow1 = new SnsFollow();
            follow1.setUserId(10L);
            SnsFollow follow2 = new SnsFollow();
            follow2.setUserId(20L);
            when(followMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(follow1, follow2));

            Set<Long> result = followService.getFollowerIds(5L);

            assertEquals(2, result.size());
            assertTrue(result.contains(10L));
            assertTrue(result.contains(20L));
            verify(redisUtils).sAdd(eq("user:followers:5"), any(String[].class));
        }

        @Test
        @DisplayName("getFollowingIds - 正常获取")
        void getFollowingIds() {
            when(redisUtils.sMembers("user:followings:1")).thenReturn(Collections.emptySet());

            SnsFollow follow = new SnsFollow();
            follow.setFollowId(100L);
            when(followMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(follow));

            Set<Long> result = followService.getFollowingIds(1L);

            assertEquals(1, result.size());
            assertTrue(result.contains(100L));
        }
    }

    @Nested
    @DisplayName("获取粉丝/关注数测试")
    class GetFollowCountsTests {

        @Test
        @DisplayName("getFollowerCount - 正常获取")
        void getFollowerCount() {
            when(redisUtils.sSize("user:followers:5")).thenReturn(42L);

            Long count = followService.getFollowerCount(5L);

            assertEquals(42L, count);
        }

        @Test
        @DisplayName("getFollowingCount - 正常获取")
        void getFollowingCount() {
            when(redisUtils.sSize("user:followings:1")).thenReturn(15L);

            Long count = followService.getFollowingCount(1L);

            assertEquals(15L, count);
        }
    }
}
