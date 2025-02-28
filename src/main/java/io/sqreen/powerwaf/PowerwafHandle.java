/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf;

public class PowerwafHandle {
    private final long nativeHandle;

    // called from JNI
    private PowerwafHandle(long handle) {
        if (handle == 0) {
            throw new IllegalArgumentException("Cannot build null PowerwafHandles");
        }
        this.nativeHandle = handle;
    }
}
