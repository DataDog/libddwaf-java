/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf;

public class WafHandle {
    private final long nativeHandle;
    private boolean online;

    // called from JNI
    private WafHandle(long handle) {
        if (handle == 0) {
            throw new IllegalArgumentException("Cannot build null WafHandles");
        }
        online = true;
        this.nativeHandle = handle;
    }

    public void destroy() {
        if (nativeHandle != 0 && online) {
            destroyWafHandle(this.nativeHandle);
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

    private static native void destroyWafHandle(long nativeWafHandle);
    private static native String[] getKnownAddresses(WafHandle handle);
    private static native String[] getKnownActions(WafHandle handle);
}
