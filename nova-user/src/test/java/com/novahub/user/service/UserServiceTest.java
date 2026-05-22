package com.novahub.user.service;

import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.JwtUtils;
import com.novahub.common.utils.RedisUtils;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.user.dto.LoginRequest;
import com.novahub.user.dto.RegisterRequest;
import com.novahub.user.dto.UpdateUserRequest;
import com.novahub.user.entity.SnsFollow;
import com.novahub.user.entity.SysUser;
import com.novahub.user.entity.SysUserRole;
import com.novahub.user.mapper.SnsFollowMapper;
import com.novahub.user.mapper.SysUserMapper;
import com.novahub.user.mapper.SysUserRoleMapper;
import com.novahub.user.service.impl.UserServiceImpl;
import com.novahub.user.vo.AuthVO;
import com.novahub.user.vo.UserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private SnsFollowMapper followMapper;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RedisUtils redisUtils;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl userService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userMapper, userRoleMapper, followMapper, jwtUtils, redisUtils, passwordEncoder);
        securityUtilsMock = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Nested
    @DisplayName("注册功能测试")
    class RegisterTests {

        @Test
        @DisplayName("注册成功 - 返回AuthVO")
        void register_success() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setNickname("New User");

            doReturn(false).when(userMapper).exists(any());
            when(jwtUtils.generateToken(anyLong(), anyString())).thenReturn("mock-token");
            doReturn(1).when(userRoleMapper).insert(any(SysUserRole.class));
            doAnswer(inv -> {
                SysUser u = inv.getArgument(0);
                u.setId(10086L);
                return 1;
            }).when(userMapper).insert(any(SysUser.class));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedhash");

            AuthVO result = userService.register(request);

            assertNotNull(result);
            assertEquals(10086L, result.getUserId());
            assertEquals("newuser", result.getUsername());
            assertEquals("New User", result.getNickname());
            assertEquals("mock-token", result.getToken());
            verify(redisUtils).set(eq("user:token:10086"), eq("mock-token"), anyLong(), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("注册失败 - 用户名已存在")
        void register_fail_usernameExists() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("existinguser");
            request.setPassword("password123");
            request.setNickname("Existing");

            doReturn(true).when(userMapper).exists(any());

            BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(request));

            assertEquals(ResultCode.AUTH_USERNAME_EXIST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("注册失败 - 手机号已注册")
        void register_fail_phoneExists() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setNickname("New User");
            request.setPhone("13800138000");

            doReturn(false).doReturn(true).when(userMapper).exists(any());

            BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(request));

            assertEquals(ResultCode.AUTH_PHONE_EXIST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("注册失败 - 邮箱已注册")
        void register_fail_emailExists() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setNickname("New User");
            request.setEmail("used@example.com");

            doReturn(false).doReturn(true).when(userMapper).exists(any());

            BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(request));

            assertEquals(ResultCode.AUTH_EMAIL_EXIST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("注册时密码被BCrypt加密")
        void register_encodesPassword() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("testuser");
            request.setPassword("plainpassword");
            request.setNickname("Test");

            doReturn(false).when(userMapper).exists(any());
            when(jwtUtils.generateToken(anyLong(), anyString())).thenReturn("token");
            doReturn(1).when(userRoleMapper).insert(any(SysUserRole.class));
            doAnswer(inv -> {
                SysUser u = inv.getArgument(0);
                u.setId(1L);
                return 1;
            }).when(userMapper).insert(any(SysUser.class));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedhash");

            userService.register(request);

            ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
            verify(userMapper).insert(captor.capture());

            SysUser savedUser = captor.getValue();
            assertNotNull(savedUser.getPassword());
            assertEquals("$2a$10$encodedhash", savedUser.getPassword());
            verify(passwordEncoder).encode("plainpassword");
        }
    }

    @Nested
    @DisplayName("登录功能测试")
    class LoginTests {

        @Test
        @DisplayName("登录成功 - 正确用户名和密码")
        void login_success() {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("correctpassword");

            SysUser user = new SysUser();
            user.setId(12345L);
            user.setUsername("testuser");
            user.setPassword("$2a$10$encodedhash");
            user.setNickname("Test User");
            user.setStatus(1);

            doReturn(user).when(userMapper).selectOne(any());
            when(passwordEncoder.matches("correctpassword", "$2a$10$encodedhash")).thenReturn(true);
            when(jwtUtils.generateToken(12345L, "testuser")).thenReturn("jwt-token");

            AuthVO result = userService.login(request);

            assertNotNull(result);
            assertEquals(12345L, result.getUserId());
            assertEquals("testuser", result.getUsername());
            assertEquals("jwt-token", result.getToken());
            verify(redisUtils).set(eq("user:token:12345"), eq("jwt-token"), anyLong(), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("登录失败 - 用户名不存在")
        void login_fail_userNotFound() {
            LoginRequest request = new LoginRequest();
            request.setUsername("nonexistent");
            request.setPassword("password");

            doReturn(null).when(userMapper).selectOne(any());

            BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(request));

            assertEquals(ResultCode.AUTH_ACCOUNT_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("登录失败 - 密码错误")
        void login_fail_wrongPassword() {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("wrongpassword");

            SysUser user = new SysUser();
            user.setId(1L);
            user.setUsername("testuser");
            user.setPassword("$2a$10$correctEncodedPassword");
            user.setStatus(1);

            doReturn(user).when(userMapper).selectOne(any());
            when(passwordEncoder.matches("wrongpassword", "$2a$10$correctEncodedPassword")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(request));

            assertEquals(ResultCode.AUTH_PASSWORD_ERROR.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("登录失败 - 账号已禁用")
        void login_fail_accountDisabled() {
            LoginRequest request = new LoginRequest();
            request.setUsername("disableduser");
            request.setPassword("password");

            SysUser user = new SysUser();
            user.setId(1L);
            user.setUsername("disableduser");
            user.setPassword("$2a$10$encodedhash");
            user.setStatus(0);

            doReturn(user).when(userMapper).selectOne(any());

            BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(request));

            assertEquals(ResultCode.AUTH_ACCOUNT_DISABLED.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("登出功能测试")
    class LogoutTests {

        @Test
        @DisplayName("登出成功")
        void logout_success() {
            String token = "valid-token";

            when(jwtUtils.getExpirationFromToken(token)).thenReturn(
                    new java.util.Date(System.currentTimeMillis() + 3600000));
            when(jwtUtils.getUserIdFromToken(token)).thenReturn(100L);

            userService.logout(token);

            verify(redisUtils).delete("user:token:100");
            verify(redisUtils).set(eq("token:blacklist:" + token), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        }
    }

    @Nested
    @DisplayName("更新用户信息测试")
    class UpdateUserTests {

        @Test
        @DisplayName("更新昵称成功")
        void updateUser_success_nickname() {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setNickname("newnickname");

            SysUser existingUser = new SysUser();
            existingUser.setId(100L);
            existingUser.setUsername("user1");
            existingUser.setNickname("oldnickname");
            existingUser.setIsDeleted(0);
            existingUser.setFollowCount(0);
            existingUser.setFansCount(0);
            existingUser.setWorksCount(0);

            SysUser updatedUser = new SysUser();
            updatedUser.setId(100L);
            updatedUser.setUsername("user1");
            updatedUser.setNickname("newnickname");
            updatedUser.setIsDeleted(0);
            updatedUser.setFollowCount(0);
            updatedUser.setFansCount(0);
            updatedUser.setWorksCount(0);

            securityUtilsMock.when(SecurityUtils::getUserId).thenReturn(100L);
            when(userMapper.selectById(100L)).thenReturn(existingUser);
            doReturn(updatedUser).when(userMapper).selectById(100L);
            doReturn(false).when(userMapper).exists(any());
            doReturn(false).when(followMapper).exists(any());
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

            UserVO result = userService.updateUser(100L, request);

            assertNotNull(result);
            assertEquals("newnickname", result.getNickname());
        }

        @Test
        @DisplayName("更新个人简介成功")
        void updateUser_success_bio() {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setBio("new bio content");

            SysUser existingUser = new SysUser();
            existingUser.setId(100L);
            existingUser.setUsername("user1");
            existingUser.setNickname("nickname");
            existingUser.setBio("old bio");
            existingUser.setIsDeleted(0);
            existingUser.setFollowCount(0);
            existingUser.setFansCount(0);
            existingUser.setWorksCount(0);

            SysUser updatedUser = new SysUser();
            updatedUser.setId(100L);
            updatedUser.setUsername("user1");
            updatedUser.setNickname("nickname");
            updatedUser.setBio("new bio content");
            updatedUser.setIsDeleted(0);
            updatedUser.setFollowCount(0);
            updatedUser.setFansCount(0);
            updatedUser.setWorksCount(0);

            securityUtilsMock.when(SecurityUtils::getUserId).thenReturn(100L);
            when(userMapper.selectById(100L)).thenReturn(existingUser);
            doReturn(updatedUser).when(userMapper).selectById(100L);
            doReturn(false).when(userMapper).exists(any());
            doReturn(false).when(followMapper).exists(any());
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

            UserVO result = userService.updateUser(100L, request);

            assertNotNull(result);
            assertEquals("new bio content", result.getBio());
        }
    }

    @Nested
    @DisplayName("用户是否存在检查测试")
    class ExistsTests {

        @Test
        @DisplayName("用户名已存在")
        void existsByUsername_true() {
            doReturn(true).when(userMapper).exists(any());

            boolean result = userService.existsByUsername("testuser");

            assertTrue(result);
        }

        @Test
        @DisplayName("用户名不存在")
        void existsByUsername_false() {
            doReturn(false).when(userMapper).exists(any());

            boolean result = userService.existsByUsername("newuser");

            assertFalse(result);
        }

        @Test
        @DisplayName("手机号已存在")
        void existsByPhone_true() {
            doReturn(true).when(userMapper).exists(any());

            boolean result = userService.existsByPhone("13800138000");

            assertTrue(result);
        }

        @Test
        @DisplayName("手机号不存在")
        void existsByPhone_false() {
            doReturn(false).when(userMapper).exists(any());

            boolean result = userService.existsByPhone("13800138001");

            assertFalse(result);
        }

        @Test
        @DisplayName("邮箱已存在")
        void existsByEmail_true() {
            doReturn(true).when(userMapper).exists(any());

            boolean result = userService.existsByEmail("test@example.com");

            assertTrue(result);
        }

        @Test
        @DisplayName("邮箱不存在")
        void existsByEmail_false() {
            doReturn(false).when(userMapper).exists(any());

            boolean result = userService.existsByEmail("new@example.com");

            assertFalse(result);
        }
    }
}
