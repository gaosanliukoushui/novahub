package com.novahub.user.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novahub.common.annotation.RequiresPermission;
import com.novahub.common.result.Result;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.user.service.IPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final IPermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        RequiresPermission annotation = hm.getMethodAnnotation(RequiresPermission.class);
        if (annotation == null) {
            return true;
        }

        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            writeForbiddenResponse(response, "用户未登录");
            return false;
        }

        boolean hasPermission = permissionService.hasPermission(userId, annotation.value());
        if (!hasPermission) {
            log.warn("权限校验失败: userId={}, permission={}, method={}",
                    userId, annotation.value(), hm.getShortLogMessage());
            writeForbiddenResponse(response, "无此操作权限: " + annotation.value());
            return false;
        }

        return true;
    }

    private void writeForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> result = Result.error(ResultCode.FORBIDDEN.getCode(), message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
