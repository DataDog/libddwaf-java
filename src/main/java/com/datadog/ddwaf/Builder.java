/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

package com.datadog.ddwaf;



import java.util.Map;

public final class Builder{

    private static final String CONFIGURATION_RULES = "configuration_rules_java_path_";
    /**
     *  The ptr field holds the pointer to PWAddContext and managed by PowerWAF
     */
    private long ptr;     // KEEP THIS FIELD!

    public Builder(WafConfig config) {
        this.ptr = initBuilder(config == null ? WafConfig.DEFAULT_CONFIG : config);
    }

    public boolean addOrUpdateConfig(Map<String, Object> definition, RuleSetInfo[] infoRef) {
         if(!addOrUpdateConfig(this, createPath(definition), definition, infoRef)){
             return false;
         }
        // at least one rule ok or no error
        return infoRef != null && infoRef[0] != null && (infoRef[0].getNumRulesError() == 0 || infoRef[0].getNumRulesOK() != 0);
    }

    private static String createPath(Map<String, Object> definition) {
        return CONFIGURATION_RULES + definition.hashCode();
    }

    private static native long initBuilder(WafConfig config);
    private static native boolean addOrUpdateConfig(Builder builder, String path, Map<String, Object> definition, RuleSetInfo[] infoRef);

}
