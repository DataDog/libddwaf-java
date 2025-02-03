/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package io.sqreen.powerwaf;

import io.sqreen.powerwaf.metrics.InputTruncatedType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class PowerwafMetrics {
    // total accumulated time between runs, including metrics
    volatile long totalRunTimeNs;
    volatile long totalDdwafRunTimeNs;
    volatile Map<InputTruncatedType, AtomicLong> wafInputsTruncatedCount;
    volatile Map<InputTruncatedType, List<Long>> wafInputsTruncatedSize;

    PowerwafMetrics() {
        this.wafInputsTruncatedCount = new HashMap<>();
        this.wafInputsTruncatedSize = new HashMap<>();
    }

    public long getTotalRunTimeNs() {
        return totalRunTimeNs;
    }

    protected void incrementTotalRunTimeNs(long increment) {
        synchronized (this) {
            totalRunTimeNs += increment;
        }
    }

    public long getTotalDdwafRunTimeNs() {
        return totalDdwafRunTimeNs;
    }

    protected void incrementTotalDdwafRunTimeNs(long increment) {
        synchronized (this) {
            totalDdwafRunTimeNs += increment;
        }
    }

    public Long getWafInputsTruncatedCount(InputTruncatedType type) {
        return wafInputsTruncatedCount.get(type) == null ? 0 : wafInputsTruncatedCount.get(type).get();
    }

    protected void incrementWafInputsTruncatedCount(InputTruncatedType type) {
        synchronized (this) {
            wafInputsTruncatedCount.putIfAbsent(type, new AtomicLong(0));
            wafInputsTruncatedCount.get(type).incrementAndGet();
        }
    }

    public List<Long> getWafInputsTruncatedSize(InputTruncatedType type) {
        return wafInputsTruncatedSize.get(type) == null ? new ArrayList<>() : wafInputsTruncatedSize.get(type);
    }

    protected void addWafInputsTruncatedSize(InputTruncatedType type, long size) {
        synchronized (this) {
            wafInputsTruncatedSize.putIfAbsent(type, new ArrayList<>());
            wafInputsTruncatedSize.get(type).add(size);
        }
    }
}
