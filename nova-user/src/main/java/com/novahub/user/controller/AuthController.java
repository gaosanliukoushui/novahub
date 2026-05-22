package com.novahub.user.controller;

import com.novahub.common.annotation.NoAuth;
import com.novahub.common.result.Result;
import com.novahub.user.dto.LoginRequest;
import com.novahub.user.dto.RegisterRequest;
import com.novahub.user.service.IUserService;
import com.novahub.user.vo.AuthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证管理")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IUserService userService;

    @NoAuth
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<AuthVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userService.register(request));
    }

    @NoAuth
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<AuthVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        userService.logout(token);
        return Result.ok();
    }
}
