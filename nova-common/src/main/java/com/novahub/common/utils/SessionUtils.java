package com.novahub.common.utils;

import jakarta.servlet.http.HttpSession;

/**
 * 分布式 Session 工具类。
 * 提供统一的 Session 属性读写接口，兼容 Spring Session + Redis 的分布式 Session 方案。
 */
public class SessionUtils {

    private SessionUtils() {}

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

    public static void invalidate(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
    }

    public static String getSessionId(HttpSession session) {
        return session == null ? null : session.getId();
    }
}
