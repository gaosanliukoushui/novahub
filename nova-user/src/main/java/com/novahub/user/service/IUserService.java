package com.novahub.user.service;

import com.novahub.user.dto.LoginRequest;
import com.novahub.user.dto.RegisterRequest;
import com.novahub.user.dto.UpdateUserRequest;
import com.novahub.user.vo.AuthVO;
import com.novahub.user.vo.UserVO;

public interface IUserService {

    AuthVO register(RegisterRequest request);

    AuthVO login(LoginRequest request);

    void logout(String token);

    UserVO getUserById(Long userId);

    UserVO getCurrentUser(Long userId);

    UserVO updateUser(Long userId, UpdateUserRequest request);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}
