/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import com.datadog.ddwaf.exception.InvalidRuleSetException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail

class BadRuleTests extends WafTestBase {

    @Test
    void 'no events'() {
        boolean pass = builder.addOrUpdateRuleConfig('enya', [version: '0.0', events: []], ruleSetInfo)
        assert pass

        assert ruleSetInfo[0].numRulesOK == 0 // passes but no rules
    }

    @Test
    void 'rule without id'() {
        def rules = copyMap(ARACHNI_ATOM_V2_1)
        rules['rules'][0].remove('id')
        shouldFail(InvalidRuleSetException) {
            builder.addOrUpdateRuleConfig('enya', rules, ruleSetInfo)
        }

        assert ruleSetInfo[0].numRulesOK == 0
        assert ruleSetInfo[0].numRulesError == 1
        assert ruleSetInfo[0].errors == ['missing key \'id\'':['index:0']]
    }

    @Test
    void 'rules have the wrong form'() {
        def rules = copyMap(ARACHNI_ATOM_V2_1)
        rules['rules'] = [:]

        shouldFail(InvalidRuleSetException) {
            builder.addOrUpdateRuleConfig('enya', rules, ruleSetInfo)
        }

        assert ruleSetInfo[0].numRulesOK == 0
        assert ruleSetInfo[0].numRulesError == 1
        assert ruleSetInfo[0].rules.error == "bad cast, expected 'array', obtained 'map'"
    }

    @Test
    void 'duplicated rule'() {
        def rules = copyMap(ARACHNI_ATOM_V2_1)
        rules['rules'] << rules['rules'][0]
        builder.addOrUpdateRuleConfig('enya', rules, ruleSetInfo)
        nativeWafHandle = builder.buildNativeWafHandleInstance(nativeWafHandle)

        assert ruleSetInfo[0].numRulesOK == 1
        assert ruleSetInfo[0].numRulesError == 1
        assert ruleSetInfo[0].errors == ['duplicate rule': ['arachni_rule'] as String[]]
    }

    private Map copyMap(Map map) {
        new JsonSlurper().parseText(JsonOutput.toJson(map))
    }

}
