/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

package com.datadog.ddwaf;



import java.util.Map;

public final class WafBuilder {

    private static final String CONFIGURATION_RULES = "configuration_rules_java_path_";
    /**
     *  The ptr field holds the pointer to PWAddContext and managed by PowerWAF
     */
    private long ptr;     // KEEP THIS FIELD!
    private String path;

    public WafBuilder(WafConfig config) {
        this.ptr = initBuilder(config == null ? WafConfig.DEFAULT_CONFIG : config);
        if (ptr == 0) { // in case the provided config does not work, we try with a default config again
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
        path =  CONFIGURATION_RULES + definition.hashCode();
        return path;
    }

    private static native long initBuilder(WafConfig config);
    private static native boolean addOrUpdateRuleConfig(WafBuilder wafBuilder, String oldPath, String path, Map<String, Object> definition, RuleSetInfo[] infoRef);


}
