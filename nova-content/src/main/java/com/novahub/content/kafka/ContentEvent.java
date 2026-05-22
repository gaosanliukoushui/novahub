package com.novahub.content.kafka;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long contentId;
    private Long userId;
    private Boolean approved;
    private String remark;
    private String eventType;
    private LocalDateTime eventTime;

    public static ContentEvent reviewSubmit(Long contentId, Long userId) {
        ContentEvent event = new ContentEvent();
        event.setContentId(contentId);
        event.setUserId(userId);
        event.setEventType("REVIEW_SUBMIT");
        event.setEventTime(LocalDateTime.now());
        return event;
    }

    public static ContentEvent reviewResult(Long contentId, Boolean approved, String remark) {
        ContentEvent event = new ContentEvent();
        event.setContentId(contentId);
        event.setApproved(approved);
        event.setRemark(remark);
        event.setEventType("REVIEW_RESULT");
        event.setEventTime(LocalDateTime.now());
        return event;
    }
}
