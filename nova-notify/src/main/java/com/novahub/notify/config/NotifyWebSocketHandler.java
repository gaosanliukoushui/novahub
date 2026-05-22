package com.novahub.notify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class NotifyWebSocketHandler extends TextWebSocketHandler {

    private static final Map<Long, WebSocketSession> USER_SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = extractUserId(session);
        if (userId != null) {
            USER_SESSIONS.put(userId, session);
            log.info("WebSocket 连接建立: userId={}, sessionId={}", userId, session.getId());
        } else {
            log.warn("WebSocket 连接建立失败：无法获取用户ID，sessionId={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = extractUserId(session);
        log.debug("WebSocket 收到消息: userId={}, payload={}", userId, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = extractUserId(session);
        if (userId != null) {
            USER_SESSIONS.remove(userId);
            log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Long userId = extractUserId(session);
        log.error("WebSocket 传输错误: userId={}, error={}", userId, exception.getMessage());
        USER_SESSIONS.remove(userId);
    }

    public static void sendToUser(Long userId, String message) {
        WebSocketSession session = USER_SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                log.debug("WebSocket 推送成功: userId={}", userId);
            } catch (IOException e) {
                log.error("WebSocket 推送失败: userId={}, error={}", userId, e.getMessage());
            }
        } else {
            log.debug("WebSocket 用户不在线: userId={}", userId);
        }
    }

    public static Set<Long> getOnlineUsers() {
        return USER_SESSIONS.keySet();
    }

    public static int getOnlineCount() {
        return USER_SESSIONS.size();
    }

    private Long extractUserId(WebSocketSession session) {
        String uri = session.getUri() != null ? session.getUri().toString() : "";
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String query = session.getUri() != null ? session.getUri().getQuery() : "";

        if (query != null && query.contains("userId=")) {
            for (String param : query.split("&")) {
                if (param.startsWith("userId=")) {
                    return Long.parseLong(param.substring(7));
                }
            }
        }
        return null;
    }
}
