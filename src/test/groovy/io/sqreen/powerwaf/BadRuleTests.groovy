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
import io.sqreen.powerwaf.exception.AbstractPowerwafException
import io.sqreen.powerwaf.exception.InvalidRuleSetException
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail

class BadRuleTests implements PowerwafTrait {

    @Test(expected = AbstractPowerwafException)
    void 'no events'() {
        ctx = Powerwaf.createContext('test', [version: '0.0', events: []])
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
        assert rsi.errors == ['missing key \'id\'':['index:0']]
    }

    @Test
    void 'rules have the wrong form'() {
        def rules = copyMap(ARACHNI_ATOM_V2_1)
        rules['rules'] = [:]
        InvalidRuleSetException exc = shouldFail(InvalidRuleSetException) {
            ctx = Powerwaf.createContext('test', rules)
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
        ctx = Powerwaf.createContext('test', rules)

        def rsi = ctx.ruleSetInfo
        assert rsi.numRulesOK == 1
        assert rsi.numRulesError == 1
        assert rsi.errors == ['duplicate rule': ['arachni_rule'] as String[]]
    }

    @Test
    void 'more than one error without exception'() {
        def rules = copyMap(BROKEN_EXCLUSIONS)
        ctx = Powerwaf.createContext('test', rules)

        def rsi = ctx.ruleSetInfo
        assert rsi.numRulesOK == 1
        assert rsi.numRulesError == 2
        assert rsi.errors == ['missing key \'id\'':['index:0']
                              , "invalid type 'array' for key 'on_match', expected 'string_view'":['arachni_rule']]
    }

    @Test
    void 'more than one type of error'() {
        def rules = copyMap(BROKEN_EXCLUSIONS)
        rules['rules'][1].remove('tags')
        InvalidRuleSetException exc = shouldFail(InvalidRuleSetException) {
            ctx = Powerwaf.createContext('test', rules)
        }

        def rsi = exc.ruleSetInfo
        assert rsi.numRulesOK == 0
        assert rsi.numRulesError == 3
        assert rsi.errors == ['missing key \'id\'':['index:0']
                              , 'missing key \'tags\'':['dummy_rule']
                              , "invalid type 'array' for key 'on_match', expected 'string_view'":['arachni_rule']]
    }

    private Map copyMap(Map map) {
        new JsonSlurper().parseText(JsonOutput.toJson(map))
    }
}
