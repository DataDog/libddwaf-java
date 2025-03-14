/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

package com.datadog.ddwaf;



import com.datadog.ddwaf.exception.UnclassifiedWafException;

import java.util.Map;

public final class WafBuilder {

    private static final String CONFIGURATION_RULES = "configuration_rules_java_path_";
    /**
     *  The ptr field holds the pointer to PWAddContext and managed by PowerWAF
     */
    private long ptr;     // KEEP THIS FIELD!
    private String path;
    private boolean online;

    public WafBuilder(WafConfig config) {
        online = true;
        config = (config == null ? WafConfig.DEFAULT_CONFIG : config);
        this.ptr = initBuilder(config);
        if (ptr == 0 && config != WafConfig.DEFAULT_CONFIG) {
            // in case the provided config does not work, we try with a default config again
            this.ptr = initBuilder(WafConfig.DEFAULT_CONFIG);
        }
    }

    public boolean addOrUpdateRuleConfig(Map<String, Object> definition, RuleSetInfo[] infoRef) {
        String oldPath = path == null ? "" : path;
         if(!addOrUpdateRuleConfig(this, oldPath, createPath(definition), definition, infoRef)){
             return false;
         }
        // at least one rule ok or no error
        return infoRef != null && infoRef[0] != null && (infoRef[0].getNumRulesError() == 0 || infoRef[0].getNumRulesOK() != 0);
    }

    private String createPath(Map<String, Object> definition) {
        path = CONFIGURATION_RULES + definition.hashCode();
        return path;
    }
    public void removeRuleConfig() {
        if(path != null) {
            removeRuleConfig(this, path);
        }
    }

    public NativeWafHandle buildNativeWafHandleInstance(NativeWafHandle oldHandle) throws UnclassifiedWafException {
        if(online) {
            return buildInstance(this, oldHandle);
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
    private static native boolean addOrUpdateRuleConfig(WafBuilder wafBuilder, String oldPath, String path, Map<String, Object> definition, RuleSetInfo[] infoRef);
    private static native void removeRuleConfig(WafBuilder wafBuilder, String oldPath);
    private static native void destroyBuilder(long builderPtr);

    public boolean isOnline() {
        return online;
    }
}
