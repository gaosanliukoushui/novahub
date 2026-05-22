package com.novahub.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.novahub.user.vo.UserVO;

import java.util.List;
import java.util.Set;

public interface IFollowService {

    void follow(Long userId, Long followId);

    void unfollow(Long userId, Long followId);

    IPage<UserVO> getFollowers(Long userId, int page, int pageSize);

    IPage<UserVO> getFollowings(Long userId, int page, int pageSize);

    boolean isFollowing(Long userId, Long followId);

    Set<Long> getFollowerIds(Long userId);

    Set<Long> getFollowingIds(Long userId);

    Long getFollowerCount(Long userId);

    Long getFollowingCount(Long userId);
}
