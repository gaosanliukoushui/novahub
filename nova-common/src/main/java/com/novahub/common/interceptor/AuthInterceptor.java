package com.novahub.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novahub.common.annotation.NoAuth;
import com.novahub.common.annotation.NoLogin;
import com.novahub.common.result.Result;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.JwtUtils;
import com.novahub.common.utils.RedisUtils;
import com.novahub.common.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_SESSION_KEY_PREFIX = "user:session:";
    private static final String SESSION_BLACKLIST_PREFIX = "session:blacklist:";

    @Value("${session.multi-device-enabled:false}")
    private boolean multiDeviceEnabled;

    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthInterceptor(JwtUtils jwtUtils, RedisUtils redisUtils, ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.redisUtils = redisUtils;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();

        if (handler instanceof HandlerMethod hm) {
            Class<?> beanType = hm.getBeanType();
            if (beanType.isAnnotationPresent(NoAuth.class) || beanType.isAnnotationPresent(NoLogin.class)) {
                return true;
            }
            if (hm.hasMethodAnnotation(NoAuth.class) || hm.hasMethodAnnotation(NoLogin.class)) {
                return true;
            }
        }

        if (isWhiteListed(requestUri)) {
            return true;
        }

        String token = extractToken(request);
        if (token == null) {
            writeUnauthorizedResponse(response, "缺少认证令牌");
            return false;
        }

        if (!jwtUtils.validateToken(token)) {
            writeUnauthorizedResponse(response, "无效的认证令牌");
            return false;
        }

        String blacklistKey = "token:blacklist:" + token;
        if (Boolean.TRUE.equals(redisUtils.hasKey(blacklistKey))) {
            writeUnauthorizedResponse(response, "令牌已失效，请重新登录");
            return false;
        }

        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            String username = jwtUtils.getUsernameFromToken(token);
            SecurityUtils.setUser(userId, username);
            MDC.put("userId", String.valueOf(userId));

            HttpSession session = request.getSession(true);
            bindSession(session, userId, username, token);

            return true;
        } catch (SecurityException e) {
            writeUnauthorizedResponse(response, e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        SecurityUtils.clear();
        MDC.remove("userId");
    }

    private void bindSession(HttpSession session, Long userId, String username, String token) {
        String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
        String currentSessionId = session.getId();

        if (!multiDeviceEnabled) {
            String existingSessionId = redisUtils.get(userSessionKey);
            if (existingSessionId != null && !existingSessionId.equals(currentSessionId)) {
                String oldSessionBlacklistKey = SESSION_BLACKLIST_PREFIX + existingSessionId;
                redisUtils.set(oldSessionBlacklistKey, "1", 30, TimeUnit.MINUTES);
                log.info("多端登录控制：用户 {} 的旧会话 {} 已被加入黑名单", userId, existingSessionId);
            }
        }

        redisUtils.set(userSessionKey, currentSessionId, 24, TimeUnit.HOURS);

        session.setAttribute("userId", userId);
        session.setAttribute("username", username);
        session.setAttribute("token", token);

        log.debug("Session 绑定：userId={}, sessionId={}", userId, currentSessionId);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private boolean isWhiteListed(String requestUri) {
        return WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> result = Result.error(ResultCode.UNAUTHORIZED.getCode(), message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    private static final List<String> WHITE_LIST = Arrays.asList(
            "/doc.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/swagger-resources/**", "/favicon.ico",
            "/webjars/**", "/knife4j/**",
            "/error", "/actuator/**",
            "/api/auth/**"
    );
}
