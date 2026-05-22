package com.novahub.common.session;

import jakarta.servlet.http.HttpSession;

/**
 * Session 工具类。
 * 提供统一的 Session 属性读写接口，屏蔽底层实现差异。
 */
public final class SessionUtils {

    private SessionUtils() {
    }

    public static void setAttribute(HttpSession session, String key, Object value) {
        if (session != null) {
            session.setAttribute(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getAttribute(HttpSession session, String key) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(key);
        return value == null ? null : (T) value;
    }

    public static void removeAttribute(HttpSession session, String key) {
        if (session != null) {
            session.removeAttribute(key);
        }
    }

    public static Long getUserId(HttpSession session) {
        return getAttribute(session, "userId");
    }

    public static void setUserId(HttpSession session, Long userId) {
        setAttribute(session, "userId", userId);
    }

    public static String getUsername(HttpSession session) {
        return getAttribute(session, "username");
    }

    public static void setUsername(HttpSession session, String username) {
        setAttribute(session, "username", username);
    }
}
