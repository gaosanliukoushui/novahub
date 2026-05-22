package com.novahub.notify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novahub.notify.config.NotifyWebSocketHandler;
import com.novahub.notify.dto.NotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final ObjectMapper objectMapper;

    public void pushToUser(NotifyMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            NotifyWebSocketHandler.sendToUser(message.getToUserId(), json);
            log.info("WebSocket 推送通知: toUserId={}, type={}", message.getToUserId(), message.getType());
        } catch (Exception e) {
            log.error("WebSocket 推送失败: toUserId={}, error={}", message.getToUserId(), e.getMessage(), e);
        }
    }

    public void pushToUsers(Long[] userIds, NotifyMessage message) {
        for (Long userId : userIds) {
            message.setToUserId(userId);
            pushToUser(message);
        }
    }
}
