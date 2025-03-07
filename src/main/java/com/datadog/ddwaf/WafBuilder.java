/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

package com.datadog.ddwaf;



import com.datadog.ddwaf.exception.AbstractWafException;
import com.datadog.ddwaf.exception.InternalWafException;
import com.datadog.ddwaf.exception.InvalidRuleSetException;
import com.datadog.ddwaf.exception.UnclassifiedWafException;

import java.util.Map;

public final class WafBuilder {
    /**
     *  The ptr field holds the pointer to PWAddContext and managed by PowerWAF
     */
    private final long ptr;     // KEEP THIS FIELD!
    private boolean online;

    public WafBuilder() {
        this(null);
    }

    public WafBuilder(WafConfig config) {
        online = true;
        config = (config == null ? WafConfig.DEFAULT_CONFIG : config);
        this.ptr = initBuilder(config);
    }

    public synchronized boolean addOrUpdateConfig(String uniqueId, Map<String, Object> definition, RuleSetInfo[] infoRef) throws InvalidRuleSetException {
        if (infoRef == null || infoRef.length == 0) {
            throw new IllegalArgumentException("Provide a non-null, fillable infoRef");
        }
         if(!addOrUpdateConfigNative(this, uniqueId, definition, infoRef)){
             throw new IllegalArgumentException("Invalid waf rule configuration");
         }
        // at least one rule ok or no error
        if(infoRef[0] != null && (infoRef[0].getNumConfigError() == 0 || infoRef[0].getNumConfigOK() != 0)) {
            return true;
        }
        throw new InvalidRuleSetException(infoRef[0], "Invalid rule set");
    }

    public synchronized void removeConfig(String uniqueId) {
        if(uniqueId != null) {
            removeConfigNative(this, uniqueId);
        }
    }

    public WafHandle buildWafHandleInstance(WafHandle oldHandle) throws AbstractWafException {
        if(online) {
            WafHandle nativeWafInstance = buildInstance(this, oldHandle);
            if (nativeWafInstance == null) {
                throw new InternalWafException("Failed to build Waf instance");
            }
            return nativeWafInstance;
        }
        throw new UnclassifiedWafException("WafBuilder is offline");
    }

    public synchronized void destroy() {
        destroyBuilder(ptr);
        online = false;
    }

    /**
     * Builds a new instance of ddwaf_handle and deletes the old handle if provided
     *
     * @param wafBuilder
     * @param oldHandle can be null if nothing is to be deleted
     * @return the new handle
     */
    private static native WafHandle buildInstance(WafBuilder wafBuilder, WafHandle oldHandle);


    private static native long initBuilder(WafConfig config);
    private static native boolean addOrUpdateConfigNative(WafBuilder wafBuilder, String path, Map<String, Object> definition, RuleSetInfo[] infoRef);
    private static native void removeConfigNative(WafBuilder wafBuilder, String oldPath);
    private static native void destroyBuilder(long builderPtr);

    public boolean isOnline() {
        return online;
    }
}
