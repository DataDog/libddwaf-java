/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package io.sqreen.powerwaf;

public class PowerwafMetrics {
    // total accumulated time between runs, including metrics
    volatile long totalRunTimeNs;
    volatile long totalDdwafRunTimeNs;

    PowerwafMetrics() { }

    public long getTotalRunTimeNs() {
        return totalRunTimeNs;
    }

    public long getTotalDdwafRunTimeNs() {
        return totalDdwafRunTimeNs;
    }
}
