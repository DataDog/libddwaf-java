/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package io.sqreen.powerwaf;

import io.sqreen.powerwaf.metrics.InputTruncatedType;

import java.util.concurrent.atomic.AtomicLong;

public class PowerwafMetrics {
    // total accumulated time between runs, including metrics
    volatile long totalRunTimeNs;
    volatile long totalDdwafRunTimeNs;
    AtomicLong wafInputsTruncatedStringTooLongCount = new AtomicLong();
    AtomicLong wafInputsTruncatedListMapTooLargeCount = new AtomicLong();
    AtomicLong wafInputsTruncatedObjectTooDeepCount = new AtomicLong();

    PowerwafMetrics() {
    }

    public long getTotalRunTimeNs() {
        return totalRunTimeNs;
    }

    public long getTotalDdwafRunTimeNs() {
        return totalDdwafRunTimeNs;
    }

    public long getWafInputsTruncatedCount(InputTruncatedType type) {
        switch (type) {
            case STRING_TOO_LONG:
                return wafInputsTruncatedStringTooLongCount.get();
            case LIST_MAP_TOO_LARGE:
                return wafInputsTruncatedListMapTooLargeCount.get();
            case OBJECT_TOO_DEEP:
                return wafInputsTruncatedObjectTooDeepCount.get();
            default:
                return 0L;
        }
    }

    protected void incrementWafInputsTruncatedCount(InputTruncatedType type) {
        switch (type) {
            case STRING_TOO_LONG:
                wafInputsTruncatedStringTooLongCount.incrementAndGet();
                return;
            case LIST_MAP_TOO_LARGE:
                wafInputsTruncatedListMapTooLargeCount.incrementAndGet();
                return;
            case OBJECT_TOO_DEEP:
                wafInputsTruncatedObjectTooDeepCount.incrementAndGet();
                return;
            default:
                return;
        }
    }
}
