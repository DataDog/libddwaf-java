/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf;

public class NativeWafHandle {
    private final long nativeHandle;
    private boolean online;

    // called from JNI
    private NativeWafHandle(long handle) {
        if (handle == 0) {
            throw new IllegalArgumentException("Cannot build null WafHandles");
        }
        online = true;
        this.nativeHandle = handle;
    }

    public void destroy() {
        if (nativeHandle != 0) {
            destroyNativeWafHandle(this.nativeHandle);
            online = false;
        }
    }

    public boolean isOnline() {
        return online;
    }

    private static native void destroyNativeWafHandle(long nativeWafHandle);
}
