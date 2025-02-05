/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package io.sqreen.powerwaf;

import io.sqreen.powerwaf.metrics.InputTruncatedType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class PowerwafMetrics {
    // total accumulated time between runs, including metrics
    volatile long totalRunTimeNs;
    volatile long totalDdwafRunTimeNs;
    volatile Map<InputTruncatedType, AtomicLong> wafInputsTruncatedCount;

    PowerwafMetrics() {
        this.wafInputsTruncatedCount = new HashMap<>();
    }

    public long getTotalRunTimeNs() {
        return totalRunTimeNs;
    }

    public long getTotalDdwafRunTimeNs() {
        return totalDdwafRunTimeNs;
    }

    public Long getWafInputsTruncatedCount(InputTruncatedType type) {
        return wafInputsTruncatedCount.get(type) == null ? 0 : wafInputsTruncatedCount.get(type).get();
    }

    protected void incrementWafInputsTruncatedCount(InputTruncatedType type) {
        wafInputsTruncatedCount.putIfAbsent(type, new AtomicLong(0));
        wafInputsTruncatedCount.get(type).incrementAndGet();
    }
}
