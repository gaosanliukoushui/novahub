package com.novahub.feed.event;

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
public class FeedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long contentId;
    private Long userId;
    private Integer contentType;
    private Integer eventType;
    private LocalDateTime eventTime;

    public static final String TOPIC = "feed-push";

    public static final int TYPE_PUBLISH = 1;
    public static final int TYPE_DELETE = 2;
}
