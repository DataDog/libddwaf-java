/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.datadog.ddwaf.exception.AbstractWafException
import com.datadog.ddwaf.exception.InvalidRuleSetException
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail

class BadRuleTests implements WafTrait {

    @Test(expected = AbstractWafException)
    void 'no events'() {
        ctx = Waf.createBuilder('test', [version: '0.0', events: []])
    }

    @Test
    void 'rule without id'() {
        def rules = copyMap(ARACHNI_ATOM_V2_1)
        rules['rules'][0].remove('id')
        InvalidRuleSetException exc = shouldFail(InvalidRuleSetException) {
            ctx = Waf.createBuilder('test', rules)
        }

        def rsi = exc.ruleSetInfo
        assert rsi.numRulesOK == 0
        assert rsi.numRulesError == 1
        assert rsi.errors == ['missing key \'id\'':['index:0']]
    }

    @Test
    void 'rules have the wrong form'() {
        def rules = copyMap(ARACHNI_ATOM_V2_1)
        rules['rules'] = [:]
        InvalidRuleSetException exc = shouldFail(InvalidRuleSetException) {
            ctx = Waf.createBuilder('test', rules)
        }

        def rsi = exc.ruleSetInfo
        assert rsi.numRulesOK == 0
        assert rsi.numRulesError == 0
        assert rsi.rules.error == "bad cast, expected 'array', obtained 'map'"
    }

    @Test
    void 'duplicated rule'() {
        def rules = copyMap(ARACHNI_ATOM_V2_1)
        rules['rules'] << rules['rules'][0]
        ctx = Waf.createBuilder('test', rules)

        def rsi = ctx.ruleSetInfo
        assert rsi.numRulesOK == 1
        assert rsi.numRulesError == 1
        assert rsi.errors == ['duplicate rule': ['arachni_rule'] as String[]]
    }

    private Map copyMap(Map map) {
        new JsonSlurper().parseText(JsonOutput.toJson(map))
    }

}
