/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package io.sqreen.powerwaf;

import java.util.Map;

public class RuleSetInfo {
    public final String fileVersion;
    public final int numRulesOK;
    public final int numRulesError;
    // map error string -> array of rule ids
    public final Map<String, String[]> errors;

    public RuleSetInfo(String fileVersion, int numRulesOK, int numRulesError, Map<String, String[]> errors) {
        this.fileVersion = fileVersion;
        this.numRulesOK = numRulesOK;
        this.numRulesError = numRulesError;
        this.errors = errors;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RuleSetInfo{");
        sb.append("fileVersion='").append(fileVersion).append('\'');
        sb.append(", numRulesOK=").append(numRulesOK);
        sb.append(", numRulesError=").append(numRulesError);
        sb.append(", errors=").append(errors);
        sb.append('}');
        return sb.toString();
    }
}
