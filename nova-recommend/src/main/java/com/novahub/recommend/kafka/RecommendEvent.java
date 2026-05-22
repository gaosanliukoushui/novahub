package com.novahub.recommend.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendEvent {

    private String eventType;
    private Long userId;
    private Long contentId;
    private Integer position;
    private String recommendWay;
    private String experimentId;
    private String bucketId;
    private String requestId;
    private Long timestamp;

    public static RecommendEvent ofExposure(Long userId, Long contentId, Integer position,
                                           String recommendWay, String experimentId,
                                           String bucketId, String requestId) {
        RecommendEvent event = new RecommendEvent();
        event.setEventType("EXPOSURE");
        event.setUserId(userId);
        event.setContentId(contentId);
        event.setPosition(position);
        event.setRecommendWay(recommendWay);
        event.setExperimentId(experimentId);
        event.setBucketId(bucketId);
        event.setRequestId(requestId);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    public static RecommendEvent ofClick(Long userId, Long contentId, String recommendWay) {
        RecommendEvent event = new RecommendEvent();
        event.setEventType("CLICK");
        event.setUserId(userId);
        event.setContentId(contentId);
        event.setRecommendWay(recommendWay);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }
}
