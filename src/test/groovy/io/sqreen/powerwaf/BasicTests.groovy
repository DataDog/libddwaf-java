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
import org.junit.Test

import static io.sqreen.powerwaf.Powerwaf.ResultWithData
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.arrayContaining
import static org.hamcrest.Matchers.arrayContainingInAnyOrder
import static org.hamcrest.Matchers.containsInAnyOrder
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

        ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)

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
        metrics = ctx.createMetrics()

        ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)

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

        assert metrics.totalRunTimeNs > 0
        assert metrics.totalDdwafRunTimeNs > 0
        assert metrics.totalRunTimeNs >= metrics.totalDdwafRunTimeNs
    }

    @Test
    void 'test blocking action'() {
        def ruleSet = ARACHNI_ATOM_BLOCK

        ctx = Powerwaf.createContext('test', ruleSet)

        ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
        assertThat awd.actions, arrayContaining('block_request')
    }

    @Test
    void 'test multiple actions'() {
        def ruleSet = slurper.parseText(JsonOutput.toJson(ARACHNI_ATOM_BLOCK))
        ruleSet['rules'][0]['on_match'] = ['aaaa', 'block_request', 'bbbb']

        ctx = Powerwaf.createContext('test', ruleSet)

        ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
        assertThat awd.actions, arrayContainingInAnyOrder('aaaa', 'block_request', 'bbbb')
    }

    @Test
    void 'test with array of string lists'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [
            attack: ['o:1:"ee":1:{}'],
            PassWord: ['Arachni'],
        ]
        ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }

    @Test
    void 'test with array'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = ['foo', 'Arachni'] as String[]
        ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }

    @Test
    void 'test null argument'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [null, 'Arachni']
        ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }

    @Test
    void 'test boolean arguments'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [true, false, 'Arachni']
        ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }

    @SuppressWarnings('EmptyClass')
    static class MyClass { }

    @Test
    void 'test unencodable arguments'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [new MyClass(), 'Arachni']
        ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
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
              "version": "1.0",
              "events": [
                {
                  "id": "arachni_rule",
                  "name": "Arachni",
                  "conditions": [
                    {
                      "operation": "match_regex",
                      "parameters": {
                        "inputs": [],
                        "regex": "Arachni"
                      }
                    }
                  ],
                  "tags": {
                    "type": "arachni_detection"
                  },
                  "action": "record"
                }
              ]
            }'''
        ctx = Powerwaf.createContext('test', ruleSet)
        assertThat ctx.usedAddresses, is([] as String[])
    }

    @Test
    void 'update rule data'() {
        def ruleSet = new JsonSlurper().parseText '''{
           "data" : "usr_data",
           "rules" : [
              {
                 "conditions" : [
                    {
                       "operator" : "ip_match",
                       "parameters" : {
                          "data" : "ip_data",
                          "inputs" : [
                             {
                                "address" : "http.client_ip"
                             }
                          ]
                       }
                    }
                 ],
                 "id" : 1,
                 "name" : "rule1",
                 "tags" : {
                    "category" : "category1",
                    "type" : "flow1"
                 }
              },
              {
                 "conditions" : [
                    {
                       "operator" : "exact_match",
                       "parameters" : {
                          "data": "usr_data",
                          "inputs" : [
                             {
                                "address" : "usr.id"
                             }
                          ]
                       }
                    }
                 ],
                 "id" : 2,
                 "name" : "rule2",
                 "tags" : {
                    "category" : "category2",
                    "type" : "flow2"
                 }
              }
           ],
           "version" : "2.1"
      }'''

        ctx = Powerwaf.createContext('test', ruleSet)

        ResultWithData res = ctx.runRules(['http.client_ip': '1.2.3.4'], limits, metrics)
        assertThat res.result, is(Powerwaf.Result.OK)

        res = ctx.runRules(['usr.id': 'paco'], limits, metrics)
        assertThat res.result, is(Powerwaf.Result.OK)

        ctx.updateRuleData([
            [
                    id: 'ip_data',
                    type: 'ip_with_expiration',
                    data: [
                        [
                                value: '1.2.3.4',
                                expiration: '0',
                        ]
                    ]
            ],
            [
                    id: 'usr_data',
                    type: 'data_with_expiration',
                    data: [
                            [
                                    value: 'paco',
                                    expiration: '0',
                            ]
                    ]

            ]
        ])

        res = ctx.runRules(['http.client_ip': '1.2.3.4'], limits, metrics)
        assertThat res.result, is(Powerwaf.Result.MATCH)

        res = ctx.runRules(['usr.id': 'paco'], limits, metrics)
        assertThat res.result, is(Powerwaf.Result.MATCH)

        assertThat ctx.usedRuleIDs as List, containsInAnyOrder('ip_data', 'usr_data')
    }

    @Test
    void 'rule toggling'() {
        def ruleSet = ARACHNI_ATOM_BLOCK

        ctx = Powerwaf.createContext('test', ruleSet)

        ctx.toggleRules(arachni_rule: false)
        Powerwaf.ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.OK)

        ctx.toggleRules(arachni_rule: true)
        awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }
}
