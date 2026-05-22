package com.novahub.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "认证结果视图对象")
public class AuthVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "JWT Token")
    private String token;

    @Schema(description = "Token过期时间（秒）")
    private Long expiration;
}
