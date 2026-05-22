package com.novahub.hotrank.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentStatsEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long contentId;
    private Long userId;
    private Integer contentType;
    private Integer eventType;
    private Long actorUserId;
    private LocalDateTime eventTime;

    public static final String TOPIC = "content-stats";
}
