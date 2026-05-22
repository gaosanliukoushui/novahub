package com.novahub.common.result;

/**
 * 统一响应状态码枚举
 */
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    /* 认证模块 10xx */
    AUTH_ACCOUNT_NOT_FOUND(1001, "账号不存在"),
    AUTH_PASSWORD_ERROR(1002, "密码错误"),
    AUTH_ACCOUNT_DISABLED(1003, "账号已被禁用"),
    AUTH_TOKEN_EXPIRED(1004, "Token 已过期"),
    AUTH_TOKEN_INVALID(1005, "Token 无效"),
    AUTH_TOKEN_BLACKLISTED(1006, "Token 已在黑名单中"),
    AUTH_CODE_ERROR(1007, "验证码错误"),
    AUTH_CODE_EXPIRED(1008, "验证码已过期"),
    AUTH_PHONE_EXIST(1009, "手机号已注册"),
    AUTH_EMAIL_EXIST(1010, "邮箱已注册"),
    AUTH_USERNAME_EXIST(1011, "用户名已存在"),

    /* 用户模块 11xx */
    USER_NOT_FOUND(1101, "用户不存在"),
    USER_CANNOT_FOLLOW_SELF(1102, "不能关注自己"),
    USER_ALREADY_FOLLOWED(1103, "已关注该用户"),
    USER_NOT_FOLLOWED(1104, "未关注该用户"),

    /* 内容模块 12xx */
    CONTENT_NOT_FOUND(1201, "内容不存在"),
    CONTENT_DRAFT_NOT_FOUND(1202, "草稿不存在"),
    CONTENT_ALREADY_PUBLISHED(1203, "内容已发布"),
    CONTENT_UNDER_REVIEW(1204, "内容正在审核中"),
    CONTENT_REVIEW_REJECTED(1205, "内容审核未通过"),
    CONTENT_CANNOT_EDIT(1206, "内容不可编辑"),
    CONTENT_NOT_AUTHOR(1207, "非内容作者无权操作"),
    CONTENT_PUBLISH_RATE_LIMITED(1208, "发布过于频繁，请稍后再试"),
    TAG_NOT_FOUND(1301, "标签不存在"),
    TAG_NAME_EXIST(1302, "标签名已存在"),

    /* 互动模块 14xx */
    LIKE_ALREADY_EXISTS(1401, "已点赞"),
    LIKE_NOT_EXISTS(1402, "未点赞"),
    COLLECT_ALREADY_EXISTS(1501, "已收藏"),
    COLLECT_NOT_EXISTS(1502, "未收藏"),
    COMMENT_NOT_FOUND(1601, "评论不存在"),
    COMMENT_CANNOT_REPLY(1602, "无法回复该评论"),
    COMMENT_DELETED(1603, "评论已删除"),

    /* 系统错误 9xxx */
    SYSTEM_ERROR(9001, "系统繁忙，请稍后重试"),
    SERVICE_UNAVAILABLE(9002, "服务暂不可用"),
    DB_ERROR(9003, "数据库操作失败"),
    REDIS_ERROR(9004, "缓存服务异常"),
    FILE_UPLOAD_ERROR(9005, "文件上传失败"),
    FILE_TYPE_NOT_ALLOWED(9006, "文件类型不允许");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
