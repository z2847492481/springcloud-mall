package com.mall.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(200, "成功"),
    PARAM_INVALID(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试"),
    SERVICE_UNAVAILABLE(503, "依赖服务暂不可用，已触发降级"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    USER_NOT_FOUND(1001, "用户不存在"),
    PASSWORD_ERROR(1002, "用户名或密码错误"),

    PRODUCT_NOT_FOUND(2001, "商品不存在"),
    PRODUCT_OFF_SHELF(2002, "商品已下架"),

    STOCK_NOT_ENOUGH(3001, "库存不足"),

    ORDER_NOT_FOUND(4001, "订单不存在"),
    ORDER_STATUS_INVALID(4002, "订单状态不允许该操作");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
