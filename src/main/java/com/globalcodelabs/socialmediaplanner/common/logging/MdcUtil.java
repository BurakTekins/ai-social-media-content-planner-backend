package com.globalcodelabs.socialmediaplanner.common.logging;

import org.slf4j.MDC;

public final class MdcUtil {

    public static final String CORRELATION_ID = "correlationId";
    public static final String BATCH_ID = "batchId";
    public static final String JOB_NAME = "jobName";
    public static final String PROVIDER = "provider";

    private MdcUtil() {
    }

    public static void putCorrelationId(String correlationId) {
        MDC.put(CORRELATION_ID, correlationId);
    }

    public static void putBatchId(String batchId) {
        MDC.put(BATCH_ID, batchId);
    }

    public static void putJobName(String jobName) {
        MDC.put(JOB_NAME, jobName);
    }

    public static void putProvider(String provider) {
        MDC.put(PROVIDER, provider);
    }

    public static void removeProvider() {
        MDC.remove(PROVIDER);
    }

    public static void clear() {
        MDC.clear();
    }
}
