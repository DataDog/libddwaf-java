/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package com.datadog.ddwaf;

import java.util.concurrent.atomic.AtomicLong;

public class WafMetrics {
    // total accumulated time between runs, including metrics
    AtomicLong totalRunTimeNs = new AtomicLong();
    AtomicLong totalDdwafRunTimeNs = new AtomicLong();
    AtomicLong truncatedStringTooLongCount = new AtomicLong();
    AtomicLong truncatedListMapTooLargeCount = new AtomicLong();
    AtomicLong truncatedObjectTooDeepCount = new AtomicLong();

    WafMetrics() {
    }

    public long getTotalRunTimeNs() {
        return totalRunTimeNs.get();
    }

    protected void addTotalRunTimeNs(long increment) {
        totalRunTimeNs.addAndGet(increment);
    }

    public long getTotalDdwafRunTimeNs() {
        return totalDdwafRunTimeNs.get();
    }

    public long getTruncatedStringTooLongCount() {
        return truncatedStringTooLongCount.get();
    }

    public long getTruncatedListMapTooLargeCount() {
        return truncatedListMapTooLargeCount.get();
    }

    public long getTruncatedObjectTooDeepCount() {
        return truncatedObjectTooDeepCount.get();
    }

    protected void incrementTruncatedStringTooLongCount() {
        truncatedStringTooLongCount.incrementAndGet();
    }

    protected void incrementTruncatedListMapTooLargeCount() {
        truncatedListMapTooLargeCount.incrementAndGet();
    }

    protected void incrementTruncatedObjectTooDeepCount() {
        truncatedObjectTooDeepCount.incrementAndGet();
    }
}
