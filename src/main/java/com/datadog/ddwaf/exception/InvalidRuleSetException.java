/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package com.datadog.ddwaf.exception;

import com.datadog.ddwaf.RuleSetInfo;

public class InvalidRuleSetException extends UnclassifiedWafException {
    public final RuleSetInfo ruleSetInfo;

    public InvalidRuleSetException(RuleSetInfo ruleSetInfo, Throwable orig) {
        super(orig);
        this.ruleSetInfo = ruleSetInfo;
    }
}
