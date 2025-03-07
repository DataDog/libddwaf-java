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
    private long ptr;     // KEEP THIS FIELD!
    private boolean online;

    public WafBuilder() {
        this(null);
    }

    public WafBuilder(WafConfig config) {
        online = true;
        config = (config == null ? WafConfig.DEFAULT_CONFIG : config);
        this.ptr = initBuilder(config);
        if (ptr == 0 && config != WafConfig.DEFAULT_CONFIG) {
            // in case the provided config does not work, we try with a default config again
            this.ptr = initBuilder(WafConfig.DEFAULT_CONFIG);
        }
    }

    public boolean addOrUpdateRuleConfig(String uniqueId, Map<String, Object> definition, RuleSetInfo[] infoRef) throws InvalidRuleSetException {
        if (infoRef == null || infoRef.length == 0) {
            throw new IllegalArgumentException("Provide a non-null, fillable infoRef");
        }
         if(!addOrUpdateRuleConfig(this, uniqueId, definition, infoRef)){
             throw new IllegalArgumentException("Invalid waf rule configuration");
         }
        // at least one rule ok or no error
        if(infoRef[0] != null && (infoRef[0].getNumRulesError() == 0 || infoRef[0].getNumRulesOK() != 0)) {
            return true;
        }
        throw new InvalidRuleSetException(infoRef[0], "Invalid rule set");
    }

    public void removeRuleConfig(String uniqueId) {
        if(uniqueId != null) {
            removeRuleConfig(this, uniqueId);
        }
    }

    public NativeWafHandle buildNativeWafHandleInstance(NativeWafHandle oldHandle) throws AbstractWafException {
        if(online) {
            NativeWafHandle nativeWafInstance = buildInstance(this, oldHandle);
            if (nativeWafInstance == null) {
                throw new InternalWafException("Failed to build Waf instance");
            }
            return nativeWafInstance;
        }
        throw new UnclassifiedWafException("WafBuilder is offline");
    }

    public void destroy() {
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
    private static native NativeWafHandle buildInstance(WafBuilder wafBuilder, NativeWafHandle oldHandle);


    private static native long initBuilder(WafConfig config);
    private static native boolean addOrUpdateRuleConfig(WafBuilder wafBuilder, String path, Map<String, Object> definition, RuleSetInfo[] infoRef);
    private static native void removeRuleConfig(WafBuilder wafBuilder, String oldPath);
    private static native void destroyBuilder(long builderPtr);

    public boolean isOnline() {
        return online;
    }
}
