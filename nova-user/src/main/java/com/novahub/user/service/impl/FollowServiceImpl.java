package com.novahub.user.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novahub.user.entity.SnsFollow;
import com.novahub.user.entity.SysUser;
import com.novahub.user.mapper.SnsFollowMapper;
import com.novahub.user.mapper.SysUserMapper;
import com.novahub.user.service.IFollowService;
import com.novahub.user.vo.UserVO;
import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.RedisUtils;
import com.novahub.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements IFollowService {

    private final SnsFollowMapper followMapper;
    private final SysUserMapper userMapper;
    private final RedisUtils redisUtils;

    private static final String FOLLOWINGS_KEY = "user:followings:%d";
    private static final String FOLLOWERS_KEY = "user:followers:%d";

    @Override
    @DS("master")
    @Transactional
    public void follow(Long userId, Long followId) {
        if (userId.equals(followId)) {
            throw new BusinessException(ResultCode.USER_CANNOT_FOLLOW_SELF);
        }

        SysUser target = userMapper.selectById(followId);
        if (target == null || target.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        boolean alreadyFollowed = followMapper.exists(
                new LambdaQueryWrapper<SnsFollow>()
                        .eq(SnsFollow::getUserId, userId)
                        .eq(SnsFollow::getFollowId, followId)
        );
        if (alreadyFollowed) {
            throw new BusinessException(ResultCode.USER_ALREADY_FOLLOWED);
        }

        SnsFollow follow = new SnsFollow();
        follow.setUserId(userId);
        follow.setFollowId(followId);
        follow.setCreateTime(LocalDateTime.now());
        followMapper.insert(follow);

        userMapper.update(null,
                new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, userId)
                        .setSql("follow_count = follow_count + 1"));
        userMapper.update(null,
                new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, followId)
                        .setSql("fans_count = fans_count + 1"));

        redisUtils.sAdd(String.format(FOLLOWINGS_KEY, userId), String.valueOf(followId));
        redisUtils.sAdd(String.format(FOLLOWERS_KEY, followId), String.valueOf(userId));

        log.info("用户 {} 关注了用户 {}", userId, followId);
    }

    @Override
    @DS("master")
    @Transactional
    public void unfollow(Long userId, Long followId) {
        int deleted = followMapper.delete(
                new LambdaQueryWrapper<SnsFollow>()
                        .eq(SnsFollow::getUserId, userId)
                        .eq(SnsFollow::getFollowId, followId)
        );
        if (deleted == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOLLOWED);
        }

        userMapper.update(null,
                new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, userId)
                        .setSql("follow_count = GREATEST(follow_count - 1, 0)"));
        userMapper.update(null,
                new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, followId)
                        .setSql("fans_count = GREATEST(fans_count - 1, 0)"));

        redisUtils.sRem(String.format(FOLLOWINGS_KEY, userId), String.valueOf(followId));
        redisUtils.sRem(String.format(FOLLOWERS_KEY, followId), String.valueOf(userId));

        log.info("用户 {} 取消关注了用户 {}", userId, followId);
    }

    @Override
    @DS("slave")
    public IPage<UserVO> getFollowers(Long userId, int page, int pageSize) {
        Long currentUserId = SecurityUtils.getUserId();

        Page<SnsFollow> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<SnsFollow> wrapper = new LambdaQueryWrapper<SnsFollow>()
                .eq(SnsFollow::getFollowId, userId)
                .orderByDesc(SnsFollow::getCreateTime);
        IPage<SnsFollow> followPage = followMapper.selectPage(p, wrapper);

        List<Long> followerIds = followPage.getRecords().stream()
                .map(SnsFollow::getUserId).collect(Collectors.toList());

        if (followerIds.isEmpty()) {
            return new Page<>(page, pageSize);
        }

        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, followerIds)
                        .eq(SysUser::getIsDeleted, 0)
        );

        Set<Long> followingSet = currentUserId == null ? Collections.emptySet() :
                followMapper.selectList(
                        new LambdaQueryWrapper<SnsFollow>()
                                .eq(SnsFollow::getUserId, currentUserId)
                                .in(SnsFollow::getFollowId, followerIds)
                ).stream().map(SnsFollow::getFollowId).collect(Collectors.toSet());

        List<UserVO> voList = users.stream().map(u ->
                UserVO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .nickname(u.getNickname())
                        .avatar(u.getAvatar())
                        .bio(u.getBio())
                        .followCount(u.getFollowCount())
                        .fansCount(u.getFansCount())
                        .worksCount(u.getWorksCount())
                        .isFollowing(followingSet.contains(u.getId()))
                        .createTime(u.getCreateTime() != null ? u.getCreateTime().toString() : null)
                        .build()
        ).collect(Collectors.toList());

        Page<UserVO> result = new Page<>(page, pageSize);
        result.setRecords(voList);
        result.setTotal(followPage.getTotal());
        return result;
    }

    @Override
    @DS("slave")
    public IPage<UserVO> getFollowings(Long userId, int page, int pageSize) {
        Long currentUserId = SecurityUtils.getUserId();

        Page<SnsFollow> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<SnsFollow> wrapper = new LambdaQueryWrapper<SnsFollow>()
                .eq(SnsFollow::getUserId, userId)
                .orderByDesc(SnsFollow::getCreateTime);
        IPage<SnsFollow> followPage = followMapper.selectPage(p, wrapper);

        List<Long> followingIds = followPage.getRecords().stream()
                .map(SnsFollow::getFollowId).collect(Collectors.toList());

        if (followingIds.isEmpty()) {
            return new Page<>(page, pageSize);
        }

        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, followingIds)
                        .eq(SysUser::getIsDeleted, 0)
        );

        Set<Long> followerSet = currentUserId == null ? Collections.emptySet() :
                followMapper.selectList(
                        new LambdaQueryWrapper<SnsFollow>()
                                .eq(SnsFollow::getUserId, currentUserId)
                                .in(SnsFollow::getFollowId, followingIds)
                ).stream().map(SnsFollow::getFollowId).collect(Collectors.toSet());

        List<UserVO> voList = users.stream().map(u ->
                UserVO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .nickname(u.getNickname())
                        .avatar(u.getAvatar())
                        .bio(u.getBio())
                        .followCount(u.getFollowCount())
                        .fansCount(u.getFansCount())
                        .worksCount(u.getWorksCount())
                        .isFollowing(followerSet.contains(u.getId()))
                        .createTime(u.getCreateTime() != null ? u.getCreateTime().toString() : null)
                        .build()
        ).collect(Collectors.toList());

        Page<UserVO> result = new Page<>(page, pageSize);
        result.setRecords(voList);
        result.setTotal(followPage.getTotal());
        return result;
    }

    @Override
    @DS("slave")
    public boolean isFollowing(Long userId, Long followId) {
        String key = String.format(FOLLOWINGS_KEY, userId);
        Boolean cached = redisUtils.sIsMember(key, String.valueOf(followId));
        if (cached != null && cached) {
            return true;
        }
        return followMapper.exists(
                new LambdaQueryWrapper<SnsFollow>()
                        .eq(SnsFollow::getUserId, userId)
                        .eq(SnsFollow::getFollowId, followId)
        );
    }

    @Override
    @DS("slave")
    public Set<Long> getFollowerIds(Long userId) {
        String key = String.format(FOLLOWERS_KEY, userId);
        Set<String> members = redisUtils.sMembers(key);
        if (members == null || members.isEmpty()) {
            List<Long> ids = followMapper.selectList(
                    new LambdaQueryWrapper<SnsFollow>()
                            .eq(SnsFollow::getFollowId, userId)
            ).stream().map(SnsFollow::getUserId).collect(Collectors.toList());
            if (!ids.isEmpty()) {
                redisUtils.sAdd(key, ids.stream().map(String::valueOf).toArray(String[]::new));
            }
            return new HashSet<>(ids);
        }
        return members.stream().map(Long::parseLong).collect(Collectors.toSet());
    }

    @Override
    @DS("slave")
    public Set<Long> getFollowingIds(Long userId) {
        String key = String.format(FOLLOWINGS_KEY, userId);
        Set<String> members = redisUtils.sMembers(key);
        if (members == null || members.isEmpty()) {
            List<Long> ids = followMapper.selectList(
                    new LambdaQueryWrapper<SnsFollow>()
                            .eq(SnsFollow::getUserId, userId)
            ).stream().map(SnsFollow::getFollowId).collect(Collectors.toList());
            if (!ids.isEmpty()) {
                redisUtils.sAdd(key, ids.stream().map(String::valueOf).toArray(String[]::new));
            }
            return new HashSet<>(ids);
        }
        return members.stream().map(Long::parseLong).collect(Collectors.toSet());
    }

    @Override
    public Long getFollowerCount(Long userId) {
        return redisUtils.sSize(String.format(FOLLOWERS_KEY, userId));
    }

    @Override
    public Long getFollowingCount(Long userId) {
        return redisUtils.sSize(String.format(FOLLOWINGS_KEY, userId));
    }
}
