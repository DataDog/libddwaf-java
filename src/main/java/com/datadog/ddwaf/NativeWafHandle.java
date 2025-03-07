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
        if (nativeHandle != 0 && online) {
            destroyNativeWafHandle(this.nativeHandle);
            online = false;
        }
    }

    public boolean isOnline() {
        return online;
    }

    public String[] getKnownAddresses() {
        return getKnownAddresses(this);
    }

    public String[] getKnownActions() {
        return getKnownActions(this);
    }

    private static native void destroyNativeWafHandle(long nativeWafHandle);
    private static native String[] getKnownAddresses(NativeWafHandle handle);
    private static native String[] getKnownActions(NativeWafHandle handle);
}
