/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package io.sqreen.powerwaf.exception;

import io.sqreen.powerwaf.RuleSetInfo;

public class InvalidRuleSetException extends UnclassifiedPowerwafException {
    public final RuleSetInfo ruleSetInfo;

    public InvalidRuleSetException(RuleSetInfo ruleSetInfo, Throwable orig) {
        super(orig);
        this.ruleSetInfo = ruleSetInfo;
    }
}
