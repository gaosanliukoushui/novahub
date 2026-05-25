package com.novahub.content.client;

import com.novahub.common.result.Result;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserClient {

    private final Map<Long, UserInfo> userCache = new ConcurrentHashMap<>();

    @Data
    public static class UserInfo {
        private Long id;
        private String nickname;
        private String avatar;
    }

    public UserInfo getUserInfo(Long userId) {
        if (userId == null) {
            return null;
        }

        return userCache.computeIfAbsent(userId, id -> {
            log.debug("获取用户信息: userId={}", id);
            UserInfo userInfo = new UserInfo();
            userInfo.setId(id);
            userInfo.setNickname("用户" + id);
            userInfo.setAvatar(null);
            return userInfo;
        });
    }

    public Map<Long, UserInfo> getUserInfoMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userIds.stream()
                .distinct()
                .collect(Collectors.toMap(id -> id, this::getUserInfo, (left, right) -> left));
    }

    public String getNickname(Long userId) {
        UserInfo userInfo = getUserInfo(userId);
        return userInfo != null ? userInfo.getNickname() : "未知用户";
    }

    public String getAvatar(Long userId) {
        UserInfo userInfo = getUserInfo(userId);
        return userInfo != null ? userInfo.getAvatar() : null;
    }

    public void invalidateCache(Long userId) {
        if (userId != null) {
            userCache.remove(userId);
        }
    }

    public void clearCache() {
        userCache.clear();
    }
}
