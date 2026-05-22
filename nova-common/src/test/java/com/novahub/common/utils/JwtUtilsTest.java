package com.novahub.common.utils;

import com.novahub.common.BaseTest;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest extends BaseTest {

    private JwtUtils jwtUtils;

    private static final String SECRET = "NovaHub2024SecretKeyForJwtTokenGenerationMustBeLongEnough256bits!";
    private static final long EXPIRATION = 86400000L;
    private static final long REFRESH_EXPIRATION = 604800000L;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtils = new JwtUtils();
        setField(jwtUtils, "secret", SECRET);
        setField(jwtUtils, "expiration", EXPIRATION);
        setField(jwtUtils, "refreshExpiration", REFRESH_EXPIRATION);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Nested
    @DisplayName("Token 生成测试")
    class TokenGenerationTests {

        @Test
        @DisplayName("生成Token - 包含userId和username")
        void generateToken_shouldContainUserIdAndUsername() {
            Long userId = 12345L;
            String username = "testuser";

            String token = jwtUtils.generateToken(userId, username);

            assertNotNull(token);
            assertFalse(token.isEmpty());
            assertEquals(userId, jwtUtils.getUserIdFromToken(token));
            assertEquals(username, jwtUtils.getUsernameFromToken(token));
        }

        @Test
        @DisplayName("生成Token - 指定TTL")
        void generateToken_withCustomTtl() {
            Long userId = 100L;
            String username = "ttluser";
            long customTtl = 3600000L;

            String token = jwtUtils.generateToken(userId, username, customTtl);

            Date expiration = jwtUtils.getExpirationFromToken(token);
            Date now = new Date();
            long diff = expiration.getTime() - now.getTime();
            assertTrue(diff > 0 && diff <= customTtl + 1000);
        }

        @Test
        @DisplayName("生成RefreshToken - 不包含username")
        void generateRefreshToken_shouldNotContainUsername() {
            Long userId = 999L;

            String refreshToken = jwtUtils.generateRefreshToken(userId);

            assertNotNull(refreshToken);
            assertEquals(userId, jwtUtils.getUserIdFromToken(refreshToken));
            assertNull(jwtUtils.getUsernameFromToken(refreshToken));
        }
    }

    @Nested
    @DisplayName("Token 解析测试")
    class TokenParsingTests {

        @Test
        @DisplayName("解析有效Token - 正确提取userId")
        void parseToken_shouldExtractUserId() {
            Long expectedUserId = 54321L;
            String token = jwtUtils.generateToken(expectedUserId, "parseuser");

            Long actualUserId = jwtUtils.getUserIdFromToken(token);

            assertEquals(expectedUserId, actualUserId);
        }

        @Test
        @DisplayName("解析有效Token - 正确提取username")
        void parseToken_shouldExtractUsername() {
            String expectedUsername = "parsetest";
            String token = jwtUtils.generateToken(1L, expectedUsername);

            String actualUsername = jwtUtils.getUsernameFromToken(token);

            assertEquals(expectedUsername, actualUsername);
        }

        @Test
        @DisplayName("解析有效Token - 正确提取过期时间")
        void parseToken_shouldExtractExpiration() {
            String token = jwtUtils.generateToken(1L, "exptest");
            Date expiration = jwtUtils.getExpirationFromToken(token);

            assertNotNull(expiration);
            assertTrue(expiration.after(new Date()));
        }

        @Test
        @DisplayName("解析过期Token - 抛出SecurityException")
        void parseExpiredToken_shouldThrowSecurityException() {
            String expiredToken = jwtUtils.generateToken(1L, "expireduser", -1000L);

            assertThrows(SecurityException.class, () -> jwtUtils.parseToken(expiredToken));
        }

        @Test
        @DisplayName("解析无效Token - 抛出SecurityException")
        void parseInvalidToken_shouldThrowSecurityException() {
            String invalidToken = "not.a.valid.jwt.token";

            assertThrows(SecurityException.class, () -> jwtUtils.parseToken(invalidToken));
        }
    }

    @Nested
    @DisplayName("Token 过期检测测试")
    class TokenExpirationTests {

        @Test
        @DisplayName("未过期Token - isTokenExpired返回false")
        void validToken_shouldNotBeExpired() {
            String token = jwtUtils.generateToken(1L, "validuser");

            assertFalse(jwtUtils.isTokenExpired(token));
        }

        @Test
        @DisplayName("已过期Token - isTokenExpired返回true")
        void expiredToken_shouldBeExpired() {
            String expiredToken = jwtUtils.generateToken(1L, "expireduser", -1000L);

            assertTrue(jwtUtils.isTokenExpired(expiredToken));
        }

        @Test
        @DisplayName("无效Token - isTokenExpired返回true")
        void invalidToken_shouldBeExpired() {
            assertTrue(jwtUtils.isTokenExpired("invalid.token.here"));
        }
    }

    @Nested
    @DisplayName("Token 校验测试")
    class TokenValidationTests {

        @Test
        @DisplayName("有效Token - validateToken返回true")
        void validToken_shouldValidate() {
            String token = jwtUtils.generateToken(1L, "validateuser");

            assertTrue(jwtUtils.validateToken(token));
        }

        @Test
        @DisplayName("过期Token - validateToken返回false")
        void expiredToken_shouldNotValidate() {
            String expiredToken = jwtUtils.generateToken(1L, "expireduser", -1000L);

            assertFalse(jwtUtils.validateToken(expiredToken));
        }

        @Test
        @DisplayName("伪造Token - validateToken返回false")
        void forgedToken_shouldNotValidate() {
            assertFalse(jwtUtils.validateToken("forged.token.signature"));
        }
    }
}
