/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package io.sqreen.powerwaf;

import java.util.concurrent.atomic.AtomicLong;

public class PowerwafMetrics {
    // total accumulated time between runs, including metrics
    AtomicLong totalRunTimeNs = new AtomicLong();
    AtomicLong totalDdwafRunTimeNs = new AtomicLong();

    PowerwafMetrics() {
    }

    public long getTotalRunTimeNs() {
        return totalRunTimeNs.get();
    }

    protected void addTotalRunTimeNs(long increment) {
        totalRunTimeNs.addAndGet(increment);
    }

    public long getTotalDdwafRunTimeNs() {
        return totalRunTimeNs.get();
    }
}
