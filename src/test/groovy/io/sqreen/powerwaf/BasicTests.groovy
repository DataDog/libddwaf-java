/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf

import groovy.json.JsonSlurper
import org.junit.Test

import static io.sqreen.powerwaf.Powerwaf.ActionWithData
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.contains
import static org.hamcrest.Matchers.is

class BasicTests implements PowerwafTrait {

    @Test
    void 'the version is correct'() {
        assert Powerwaf.version =~ Powerwaf.LIB_VERSION
    }

    @Test
    void 'test running basic rule v1_0'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ctx = Powerwaf.createContext('test', ruleSet)

        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni']], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)

        def json = slurper.parseText(awd.data)

        assert json[0].rule.id == 'arachni_rule'
        assert json[0].rule.name == 'Arachni'
        assert json[0].rule.tags == [category: '', type: 'arachni_detection']
        assert json[0].rule_matches[0]['operator'] == 'match_regex'
        assert json[0].rule_matches[0]['operator_value'] == 'Arachni'
        assert json[0].rule_matches[0]['parameters'][0].address == 'server.request.headers.no_cookies'
        assert json[0].rule_matches[0]['parameters'][0].key_path == ['user-agent']
        assert json[0].rule_matches[0]['parameters'][0].value == 'Arachni'
        assert json[0].rule_matches[0]['parameters'][0].highlight == ['Arachni']

        def rsi = ctx.ruleSetInfo
        assert rsi.numRulesOK == 1
        assert rsi.numRulesError == 0
        assert rsi.errors == [:]
        assert rsi.fileVersion == null
    }

    @Test
    void 'test running basic rule v2_1'() {
        def ruleSet = ARACHNI_ATOM_V2_1

        ctx = Powerwaf.createContext('test', ruleSet)

        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)

        def json = slurper.parseText(awd.data)

        assert json[0].rule.id == 'arachni_rule'
        assert json[0].rule.name == 'Arachni'
        assert json[0].rule.tags == [category: 'attack_attempt', type: 'security_scanner']
        assert json[0].rule_matches[0]['operator'] == 'match_regex'
        assert json[0].rule_matches[0]['operator_value'] == '^Arachni\\/v'
        assert json[0].rule_matches[0]['parameters'][0].address == 'server.request.headers.no_cookies'
        assert json[0].rule_matches[0]['parameters'][0].key_path == ['user-agent']
        assert json[0].rule_matches[0]['parameters'][0].value == 'Arachni/v1'
        assert json[0].rule_matches[0]['parameters'][0].highlight == ['Arachni/v']

        def rsi = ctx.ruleSetInfo
        assert rsi.numRulesOK == 1
        assert rsi.numRulesError == 0
        assert rsi.errors == [:]
        assert rsi.fileVersion == '1.2.6'
    }

    @Test
    void 'test with array of string lists'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [
            attack: ['o:1:"ee":1:{}'],
            PassWord: ['Arachni'],
        ]
        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }

    @Test
    void 'test with array'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = ['foo', 'Arachni'] as String[]
        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }

    @Test
    void 'test null argument'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [null, 'Arachni']
        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }

    @Test
    void 'test boolean arguments'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [true, false, 'Arachni']
        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }

    @SuppressWarnings('EmptyClass')
    static class MyClass { }

    @Test
    void 'test unencodable arguments'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [new MyClass(), 'Arachni']
        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }

    @Test
    void 'can retrieve used addresses'() {
        ctx = Powerwaf.createContext('test', ARACHNI_ATOM_V2_1)
        assertThat ctx.usedAddresses as List, contains('server.request.headers.no_cookies')
    }

    @Test
    void 'handles ruleset without addresses'() {
        def ruleSet = new JsonSlurper().parseText '''
            {
              "version": "2.1",
              "rules": [
                {
                  "id": "arachni_rule",
                  "name": "Arachni",
                  "tags": {
                    "type": "security_scanner",
                    "category": "attack_attempt"
                  },
                  "conditions": [
                    {
                      "parameters": {
                        "inputs": [],
                        "regex": "^Arachni\\\\\\\\/v"
                      },
                      "operator": "match_regex"
                    }
                  ],
                  "transformers": []
                }
              ]

            }'''
        ctx = Powerwaf.createContext('test', ruleSet)
        assertThat ctx.usedAddresses, is([] as String[])
    }
}
