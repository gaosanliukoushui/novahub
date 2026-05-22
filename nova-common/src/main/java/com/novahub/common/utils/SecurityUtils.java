package com.novahub.common.utils;

import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;

public class SecurityUtils {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static String getUsername() {
        return USERNAME_HOLDER.get();
    }

    public static void setUsername(String username) {
        USERNAME_HOLDER.set(username);
    }

    public static void setUser(Long userId, String username) {
        setUserId(userId);
        setUsername(username);
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
        USERNAME_HOLDER.remove();
    }

    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static void checkLogin() {
        if (getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }
}
