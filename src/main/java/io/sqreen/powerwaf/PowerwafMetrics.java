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

public class PowerwafMetrics {
    // total accumulated time between runs, including metrics
    volatile long totalRunTimeNs;
    volatile long totalDdwafRunTimeNs;
    volatile Map<ByteBufferSerializer.Arena, Map<InputTruncatedType, Long>> wafInputsTruncated;

    PowerwafMetrics() {
        this.wafInputsTruncated = new HashMap<>();
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

    public Map<InputTruncatedType, Long> getWafInputsTruncated() {
        Map<InputTruncatedType, Long> result = new HashMap<>();
        // Flatten the maps of arenas
        synchronized (this) {
            for (Map.Entry<ByteBufferSerializer.Arena, Map<InputTruncatedType, Long>> entry : wafInputsTruncated.entrySet()) {
                result.putAll(entry.getValue());
            }
        }
        return result;
    }

    protected void incrementWafInputsTruncated(ByteBufferSerializer.Arena arena, InputTruncatedType type) {
        synchronized (this) {
            wafInputsTruncated.putIfAbsent(arena, new HashMap<>());
            if ((type.equals(InputTruncatedType.LIST_MAP_TOO_LARGE) || type.equals(InputTruncatedType.OBJECT_TOO_DEEP)) && wafInputsTruncated.get(arena).containsKey(type)) {
                return;
            }
            wafInputsTruncated.get(arena).put(type, wafInputsTruncated.get(arena).getOrDefault(type, 0L) + 1);
        }
    }
}
