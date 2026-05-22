package com.novahub.notify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;

    private Long id;

    private Long fromUserId;

    private String fromUserNickname;

    private String fromUserAvatar;

    private Long toUserId;

    private String content;

    private Long targetId;

    private String targetType;

    private Long timestamp;
}
