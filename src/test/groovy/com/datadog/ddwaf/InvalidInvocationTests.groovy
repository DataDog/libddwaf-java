/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import com.datadog.ddwaf.exception.InvalidRuleSetException
import com.datadog.ddwaf.exception.UnclassifiedWafException
import groovy.transform.CompileStatic
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.containsString

class InvalidInvocationTests extends WafTestBase {
    @CompileStatic
    static class BadMap<K, V> implements Map<K, V> {
        @Delegate
        Map<K, V> delegate

        @Override
        Set<Entry<K, V>> entrySet() {
            throw new IllegalStateException('error here')
        }
    }

    @Test
    void 'force exception during conversion of rule definitions'() {
        def exc = shouldFail(IllegalStateException) {
            builder.addOrUpdateRuleConfig('enya', new BadMap(delegate: [version: '1.0', events: []]), ruleSetInfo)
        }
        assert exc.message =~ 'error here'
    }

    @Test
    void 'runRule with conversion throwing exception'() {
        builder.addOrUpdateRuleConfig('enya', ARACHNI_ATOM_V2_1, ruleSetInfo)
        nativeWafHandle = builder.buildNativeWafHandleInstance(nativeWafHandle)
        def exc = shouldFail(UnclassifiedWafException) {
            Waf.runRules(new BadMap(delegate: [:]), limits, wafMetrics, nativeWafHandle)
        }
        assert exc.cause.message =~ 'Exception encoding parameters'
        assert exc.cause.cause instanceof IllegalStateException
        assert exc.cause.cause.message == 'error here'
    }

    @Test
    void 'rule is run on destroyed builder'() {
        builder.addOrUpdateRuleConfig('enya', ARACHNI_ATOM_V2_1, ruleSetInfo)
        builder.destroy()
        def exc = shouldFail(UnclassifiedWafException) {
            nativeWafHandle = builder.buildNativeWafHandleInstance(nativeWafHandle)
        }
        assertThat exc.message, containsString('WafBuilder is offline')
        builder = new WafBuilder()
    }

    @Test
    void 'error converting update spec'() {
        builder.addOrUpdateRuleConfig('enya', ARACHNI_ATOM_V2_1, ruleSetInfo)
        nativeWafHandle = builder.buildNativeWafHandleInstance(nativeWafHandle)
        def exc = shouldFail(UnclassifiedWafException) {
            Waf.runRules(new BadMap(delegate: [arachni_rule: false]), limits, wafMetrics, nativeWafHandle)
        }
        assert exc.cause.message =~ 'Exception encoding parameters'
    }

    @Test
    void 'empty update call'() {
        builder.addOrUpdateRuleConfig('enya', ARACHNI_ATOM_V2_1, ruleSetInfo)
        nativeWafHandle = builder.buildNativeWafHandleInstance(nativeWafHandle)
        def result = builder.addOrUpdateRuleConfig('enya', [foo: 'bar'], ruleSetInfo)
        assert result // no rules to update is a passing call
    }

    @Test
    void 'invalid update call'() {
        builder.addOrUpdateRuleConfig('enya', ARACHNI_ATOM_V2_1, ruleSetInfo)
        nativeWafHandle = builder.buildNativeWafHandleInstance(nativeWafHandle)
        shouldFail(InvalidRuleSetException) {
            builder.addOrUpdateRuleConfig('enya', [rules: [[id: 'foobar']]], ruleSetInfo)
        }
    }
}
