package com.novahub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新用户信息请求")
public class UpdateUserRequest {

    @Size(min = 1, max = 64, message = "昵称长度1-64位")
    @Schema(description = "昵称")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @Pattern(regexp = "^$|^\\w+@\\w+\\.\\w+$", message = "邮箱格式不正确")
    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像URL")
    private String avatar;

    @Size(max = 255, message = "简介最多255字")
    @Schema(description = "个人简介")
    private String bio;
}
