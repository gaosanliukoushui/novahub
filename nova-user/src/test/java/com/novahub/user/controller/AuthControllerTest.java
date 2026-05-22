package com.novahub.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novahub.common.exception.BusinessException;
import com.novahub.common.exception.GlobalExceptionHandler;
import com.novahub.common.result.ResultCode;
import com.novahub.user.dto.LoginRequest;
import com.novahub.user.dto.RegisterRequest;
import com.novahub.user.service.IUserService;
import com.novahub.user.vo.AuthVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IUserService userService;

    @InjectMocks
    private AuthController authController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("注册成功 - 返回200和AuthVO")
        void register_success() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setNickname("New User");

            AuthVO authVO = AuthVO.builder()
                    .userId(10086L)
                    .username("newuser")
                    .nickname("New User")
                    .token("mock-jwt-token")
                    .expiration(86400L)
                    .build();

            when(userService.register(any(RegisterRequest.class))).thenReturn(authVO);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.userId").value(10086))
                    .andExpect(jsonPath("$.data.username").value("newuser"))
                    .andExpect(jsonPath("$.data.nickname").value("New User"))
                    .andExpect(jsonPath("$.data.token").value("mock-jwt-token"));

            verify(userService).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("注册失败 - 用户名已存在")
        void register_fail_usernameExists() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("existinguser");
            request.setPassword("password123");
            request.setNickname("Existing");

            when(userService.register(any(RegisterRequest.class)))
                    .thenThrow(new BusinessException(ResultCode.AUTH_USERNAME_EXIST));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.AUTH_USERNAME_EXIST.getCode()));
        }

        @Test
        @DisplayName("注册失败 - 参数校验失败")
        void register_fail_validationError() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("");
            request.setPassword("123");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).register(any());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginTests {

        @Test
        @DisplayName("登录成功 - 返回200和AuthVO")
        void login_success() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("correctpassword");

            AuthVO authVO = AuthVO.builder()
                    .userId(12345L)
                    .username("testuser")
                    .nickname("Test User")
                    .token("login-jwt-token")
                    .expiration(86400L)
                    .build();

            when(userService.login(any(LoginRequest.class))).thenReturn(authVO);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.userId").value(12345))
                    .andExpect(jsonPath("$.data.username").value("testuser"))
                    .andExpect(jsonPath("$.data.token").value("login-jwt-token"));

            verify(userService).login(any(LoginRequest.class));
        }

        @Test
        @DisplayName("登录失败 - 账号不存在")
        void login_fail_accountNotFound() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("nonexistent");
            request.setPassword("password");

            when(userService.login(any(LoginRequest.class)))
                    .thenThrow(new BusinessException(ResultCode.AUTH_ACCOUNT_NOT_FOUND));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.AUTH_ACCOUNT_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("登录失败 - 密码错误")
        void login_fail_wrongPassword() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("wrongpassword");

            when(userService.login(any(LoginRequest.class)))
                    .thenThrow(new BusinessException(ResultCode.AUTH_PASSWORD_ERROR));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.AUTH_PASSWORD_ERROR.getCode()));
        }

        @Test
        @DisplayName("登录失败 - 账号已禁用")
        void login_fail_accountDisabled() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("disableduser");
            request.setPassword("password");

            when(userService.login(any(LoginRequest.class)))
                    .thenThrow(new BusinessException(ResultCode.AUTH_ACCOUNT_DISABLED));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.AUTH_ACCOUNT_DISABLED.getCode()));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("登出成功 - 返回200")
        void logout_success() throws Exception {
            String token = "Bearer valid-token";
            doNothing().when(userService).logout("valid-token");

            mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(userService).logout("valid-token");
        }
    }
}
