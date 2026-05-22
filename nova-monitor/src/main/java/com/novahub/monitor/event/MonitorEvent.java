package com.novahub.monitor.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String eventType;
    private Long userId;
    private Long targetId;
    private String targetType;
    private String page;
    private String sessionId;
    private String device;
    private Long timestamp;

    public enum EventType {
        PAGE_VIEW("PAGE_VIEW"),
        LIKE("LIKE"),
        UNLIKE("UNLIKE"),
        COLLECT("COLLECT"),
        COMMENT("COMMENT"),
        PUBLISH("PUBLISH"),
        FOLLOW("FOLLOW"),
        UNFOLLOW("UNFOLLOW"),
        SHARE("SHARE"),
        SEARCH("SEARCH");

        private final String value;

        EventType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
