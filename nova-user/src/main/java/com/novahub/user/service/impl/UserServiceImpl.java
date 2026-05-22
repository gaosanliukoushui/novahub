package com.novahub.user.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novahub.user.dto.LoginRequest;
import com.novahub.user.dto.RegisterRequest;
import com.novahub.user.dto.UpdateUserRequest;
import com.novahub.user.entity.SnsFollow;
import com.novahub.user.entity.SysUser;
import com.novahub.user.entity.SysUserRole;
import com.novahub.user.mapper.SnsFollowMapper;
import com.novahub.user.mapper.SysUserMapper;
import com.novahub.user.mapper.SysUserRoleMapper;
import com.novahub.user.service.IUserService;
import com.novahub.user.vo.AuthVO;
import com.novahub.user.vo.UserVO;
import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.JwtUtils;
import com.novahub.common.utils.RedisUtils;
import com.novahub.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SnsFollowMapper followMapper;
    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.expiration:86400000}")
    private long tokenExpiration;

    private static final String ROLE_USER_CODE = "ROLE_USER";

    @Override
    @DS("master")
    @Transactional
    public AuthVO register(RegisterRequest request) {
        if (existsByUsername(request.getUsername())) {
            throw new BusinessException(ResultCode.AUTH_USERNAME_EXIST);
        }
        if (request.getPhone() != null && !request.getPhone().isEmpty() && existsByPhone(request.getPhone())) {
            throw new BusinessException(ResultCode.AUTH_PHONE_EXIST);
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty() && existsByEmail(request.getEmail())) {
            throw new BusinessException(ResultCode.AUTH_EMAIL_EXIST);
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        user.setFollowCount(0);
        user.setFansCount(0);
        user.setWorksCount(0);
        user.setIsDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);

        assignDefaultRole(user.getId());

        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        String tokenKey = "user:token:" + user.getId();
        redisUtils.set(tokenKey, token, tokenExpiration, TimeUnit.MILLISECONDS);

        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());

        return AuthVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .token(token)
                .expiration(tokenExpiration / 1000)
                .build();
    }

    @Override
    @DS("master")
    @Transactional
    public AuthVO login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
                        .eq(SysUser::getIsDeleted, 0)
        );

        if (user == null) {
            throw new BusinessException(ResultCode.AUTH_ACCOUNT_NOT_FOUND);
        }

        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.AUTH_ACCOUNT_DISABLED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.AUTH_PASSWORD_ERROR);
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        String tokenKey = "user:token:" + user.getId();
        redisUtils.set(tokenKey, token, tokenExpiration, TimeUnit.MILLISECONDS);

        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

        return AuthVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .token(token)
                .expiration(tokenExpiration / 1000)
                .build();
    }

    @Override
    public void logout(String token) {
        String blacklistKey = "token:blacklist:" + token;
        long ttl = jwtUtils.getExpirationFromToken(token).getTime() - System.currentTimeMillis();
        if (ttl > 0) {
            redisUtils.set(blacklistKey, "1", ttl, TimeUnit.MILLISECONDS);
        }
        Long userId = jwtUtils.getUserIdFromToken(token);
        if (userId != null) {
            redisUtils.delete("user:token:" + userId);
        }
        log.info("用户退出登录: userId={}", userId);
    }

    @Override
    @DS("slave")
    public UserVO getUserById(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        Long currentUserId = SecurityUtils.getUserId();
        Boolean isFollowing = null;
        if (currentUserId != null && !currentUserId.equals(userId)) {
            isFollowing = followMapper.exists(
                    new LambdaQueryWrapper<SnsFollow>()
                            .eq(SnsFollow::getUserId, currentUserId)
                            .eq(SnsFollow::getFollowId, userId)
            );
        }
        return toUserVO(user, isFollowing);
    }

    @Override
    @DS("slave")
    public UserVO getCurrentUser(Long userId) {
        return getUserById(userId);
    }

    @Override
    @DS("master")
    @Transactional
    public UserVO updateUser(Long userId, UpdateUserRequest request) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getPhone() != null) {
            if (!request.getPhone().isEmpty() && existsByPhone(request.getPhone())) {
                throw new BusinessException(ResultCode.AUTH_PHONE_EXIST);
            }
            user.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            if (!request.getEmail().isEmpty() && existsByEmail(request.getEmail())) {
                throw new BusinessException(ResultCode.AUTH_EMAIL_EXIST);
            }
            user.setEmail(request.getEmail());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户信息更新: userId={}", userId);
        return getUserById(userId);
    }

    @Override
    @DS("master")
    public boolean existsByUsername(String username) {
        return userMapper.exists(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getIsDeleted, 0));
    }

    @Override
    public boolean existsByPhone(String phone) {
        return phone != null && !phone.isEmpty() &&
                userMapper.exists(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPhone, phone)
                        .eq(SysUser::getIsDeleted, 0));
    }

    @Override
    public boolean existsByEmail(String email) {
        return email != null && !email.isEmpty() &&
                userMapper.exists(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEmail, email)
                        .eq(SysUser::getIsDeleted, 0));
    }

    private void assignDefaultRole(Long userId) {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(2L);
        userRoleMapper.insert(userRole);
    }

    private UserVO toUserVO(SysUser user, Boolean isFollowing) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .bio(user.getBio())
                .followCount(user.getFollowCount())
                .fansCount(user.getFansCount())
                .worksCount(user.getWorksCount())
                .isFollowing(isFollowing)
                .createTime(user.getCreateTime() != null ? user.getCreateTime().toString() : null)
                .build();
    }
}
