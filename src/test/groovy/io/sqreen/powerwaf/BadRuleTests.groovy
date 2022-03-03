/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.sqreen.powerwaf.exception.InvalidRuleSetException
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail

class BadRuleTests implements PowerwafTrait {

    @Test(expected = IllegalArgumentException)
    void 'no events'() {
        ctx = Powerwaf.createContext('test', [version: '0.0', events: []])
    }

    @Test(expected = IllegalArgumentException)
    void 'version is not a string'() {
        def rules = copyMap(ARACHNI_ATOM_V1_0)
        rules['version'] = 99
        ctx = Powerwaf.createContext('test', rules)
    }

    @Test
    void 'rule without id'() {
        def rules = copyMap(ARACHNI_ATOM_V2_1)
        rules['rules'][0].remove('id')
        InvalidRuleSetException exc = shouldFail(InvalidRuleSetException) {
            ctx = Powerwaf.createContext('test', rules)
        }

        def rsi = exc.ruleSetInfo
        assert rsi.numRulesOK == 0
        assert rsi.numRulesError == 1
        assert rsi.errors == [:]
    }

    @Test
    void 'duplicated rule'() {
        def rules = copyMap(ARACHNI_ATOM_V2_1)
        rules['rules'] << rules['rules'][0]
        ctx = Powerwaf.createContext('test', rules)

        def rsi = ctx.ruleSetInfo
        assert rsi.numRulesOK == 1
        assert rsi.numRulesError == 1
        assert rsi.errors == ['duplicate rule': ['arachni_rule'] as String[]]
    }

    private Map copyMap(Map map) {
        new JsonSlurper().parseText(JsonOutput.toJson(map))
    }

}
