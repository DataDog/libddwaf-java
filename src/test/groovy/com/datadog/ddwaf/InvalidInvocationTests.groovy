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
            ruleSetInfo = builder.addOrUpdateConfig('enya', new BadMap(delegate: [version: '1.0', events: []]))
        }
        assert exc.message =~ 'error here'
    }

    @Test
    void 'runRule with conversion throwing exception'() {
        ruleSetInfo = builder.addOrUpdateConfig('enya', ARACHNI_ATOM_V2_1)
        def exc = shouldFail(UnclassifiedWafException) {
            Waf.runContext(new BadMap(delegate: [:]), limits, wafMetrics, builder)
        }
        assert exc.cause.message =~ 'Exception encoding parameters'
        assert exc.cause.cause instanceof IllegalStateException
        assert exc.cause.cause.message == 'error here'
    }

    @Test
    void 'rule is run on destroyed builder'() {
        ruleSetInfo = builder.addOrUpdateConfig('enya', ARACHNI_ATOM_V2_1)
        builder.destroy()
        def exc = shouldFail(UnclassifiedWafException) {
            Waf.runContext(['server.request.headers.no_cookies': ['user-agent': ['Arachni/v1']]], limits, wafMetrics,
                    builder)
        }
        assertThat exc.message, containsString('WafBuilder is offline')
        builder = new WafBuilder()
    }

    @Test
    void 'error converting update spec'() {
        ruleSetInfo = builder.addOrUpdateConfig('enya', ARACHNI_ATOM_V2_1)
        def exc = shouldFail(UnclassifiedWafException) {
            Waf.runContext(new BadMap(delegate: [arachni_rule: false]), limits, wafMetrics, builder)
        }
        assert exc.cause.message =~ 'Exception encoding parameters'
    }

    @Test
    void 'empty update call'() {
        ruleSetInfo = builder.addOrUpdateConfig('enya', ARACHNI_ATOM_V2_1)
        ruleSetInfo = builder.addOrUpdateConfig('enya', [foo: 'bar'])
        assert ruleSetInfo.numConfigOK == 0
        assert ruleSetInfo.numConfigError == 0 // nothing happened
    }

    @Test
    void 'invalid update call'() {
        ruleSetInfo = builder.addOrUpdateConfig('enya', ARACHNI_ATOM_V2_1)
        InvalidRuleSetException exc = shouldFail(InvalidRuleSetException) {
            builder.addOrUpdateConfig('enya', [rules: [[id: 'foobar']]])
        }
        assert exc.ruleSetInfo.allErrors.size() == 1
        assert exc.ruleSetInfo.allErrors.keySet().contains('missing key \'conditions\'')
    }
}
