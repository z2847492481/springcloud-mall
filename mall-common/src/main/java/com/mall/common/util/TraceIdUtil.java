package com.mall.common.util;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceIdUtil {

    private static final String TRACE_ID = "traceId";

    private TraceIdUtil() {
    }

    public static String get() {
        return MDC.get(TRACE_ID);
    }

    public static void set(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    public static void clear() {
        MDC.remove(TRACE_ID);
    }

    public static String getOrCreate() {
        String traceId = get();
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            set(traceId);
        }
        return traceId;
    }
}
