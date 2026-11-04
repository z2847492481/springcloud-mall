package com.mall.common.constant;

public final class CommonConstants {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    public static final String TOPIC_ORDER_CREATED = "order-created";
    public static final String TOPIC_ORDER_SHIPPED = "order-shipped";
    public static final String TOPIC_ORDER_CANCELLED = "order-cancelled";

    private CommonConstants() {
    }
}
