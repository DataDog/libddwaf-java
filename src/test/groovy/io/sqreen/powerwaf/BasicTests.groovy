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
import static org.hamcrest.Matchers.contains
import static org.hamcrest.Matchers.containsInAnyOrder
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.empty
import static org.hamcrest.Matchers.notNullValue

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
        assert json[0].rule.tags == [type: 'arachni_detection']
        assert json[0].rule_matches[0]['operator'] == 'match_regex'
        assert json[0].rule_matches[0]['operator_value'] == 'Arachni'
        assert json[0].rule_matches[0]['parameters'][0].address == 'server.request.headers.no_cookies'
        assert json[0].rule_matches[0]['parameters'][0].key_path == ['user-agent']
        assert json[0].rule_matches[0]['parameters'][0].value == 'Arachni'
        assert json[0].rule_matches[0]['parameters'][0].highlight == ['Arachni']

        def rsi = ctx.ruleSetInfo
        assert rsi.rules.loaded == ['arachni_rule']
        assert rsi.numRulesOK == 1
        assert rsi.numRulesError == 0
        assert rsi.errors == [:]
        assert rsi.rulesetVersion == null
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
        assert rsi.rulesetVersion == '1.2.6'

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
        assertThat awd.actions.size(), is(1)
        assertThat awd.actions.keySet(), hasItem('block_request')
        assertThat awd.actions.get('block_request').type, is('auto')
        assertThat awd.actions.get('block_request').status_code, is('403')
        assertThat awd.actions.get('block_request').grpc_status_code, is('10')
    }

    @Test
    void 'test built-in actions'() {
        def ruleSet = ARACHNI_ATOM_V2_1
        ruleSet['rules'][0]['on_match'] = ['block', 'stack_trace', 'extract_schema']

        ctx = Powerwaf.createContext('test', ruleSet)

        ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
        assertThat awd.actions.size(), is(3)

        // block action
        assertThat awd.actions.keySet(), hasItem('block_request')
        assertThat awd.actions.get('block_request').type, is('auto')
        assertThat awd.actions.get('block_request').status_code, is('403')
        assertThat awd.actions.get('block_request').grpc_status_code, is('10')

        // stack_trace action
        assertThat awd.actions.keySet(), hasItem('generate_stack')
        assertThat awd.actions.get('generate_stack').stack_id, is(notNullValue())

        // extract_schema action
        assertThat awd.actions.keySet(), hasItem('generate_schema')
    }

    @Test
    void 'test multiple actions'() {
        def ruleSet = slurper.parseText(JsonOutput.toJson(ARACHNI_ATOM_BLOCK))
        ruleSet.putAt('actions', [
            [
                id: 'aaaa',
                parameters: [
                    status_code: '200',
                    type: 'auto',
                    grpc_status_code: '10',
                ],
                type: 'aaaa'
            ],
            [
                id: 'bbbb',
                parameters: [
                    status_code: '200',
                    type: 'auto',
                    grpc_status_code: '10',
                ],
                type: 'bbbb'
            ]
        ])
        ruleSet['rules'][0]['on_match'] = ['aaaa', 'block', 'bbbb']

        ctx = Powerwaf.createContext('test', ruleSet)

        ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
        assertThat awd.actions.keySet(), containsInAnyOrder('aaaa', 'block_request', 'bbbb')
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
        assertThat ctx.usedAddresses as List, is(empty())
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
                 "id" : "1",
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
                 "id" : "2" ,
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

        def newData = [
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
        ]
        ctx.withCloseable {
            ctx = ctx.update('test2', [rules_data: newData])
        }

        res = ctx.runRules(['http.client_ip': '1.2.3.4'], limits, metrics)
        assertThat res.result, is(Powerwaf.Result.MATCH)

        res = ctx.runRules(['usr.id': 'paco'], limits, metrics)
        assertThat res.result, is(Powerwaf.Result.MATCH)
    }

    @Test
    void 'input exclusion'() {
        def ruleSet = new JsonSlurper().parseText '''{
           "version": "2.2",
           "metadata": {
             "rules_version": "1.2.6"
           },
           "exclusions": [
             {
               "id": "1",
               "rules_target": [
                 {
                   "tags": {
                     "type": "path-exclusion-flow"
                   }
                 }
               ],
               "conditions": [
                 {
                   "operator": "match_regex",
                   "parameters": {
                     "inputs": [
                       {
                         "address": "server.request.query",
                         "key_path": ["activate_exclusion"]
                       }
                     ],
                     "regex": "true"
                   }
                 }
               ],
               "inputs": [
                 {
                   "address": "server.request.query",
                   "key_path": ["excluded_key"]
                 }
               ]
             }

           ],
           "rules": [
             {
               "id": "rule-for-path-exclusion",
               "name": "rule-for-path-exclusion",
               "tags": {
                 "type": "path-exclusion-flow"
               },
               "conditions": [
                 {
                   "operator": "match_regex",
                   "parameters": {
                     "inputs": [
                       {
                         "address": "server.request.query",
                         "key_path": [
                           "excluded_key"
                         ]
                       }
                     ],
                     "regex": "true"
                   }
                 }
               ]
             }
           ]
         }'''

        ctx = Powerwaf.createContext('test', ruleSet)

        ResultWithData res = ctx.runRules(['server.request.query': [excluded_key: 'true']], limits, metrics)
        assertThat res.result, is(Powerwaf.Result.MATCH)

        res = ctx.runRules(
                ['server.request.query': [excluded_key: 'true', activate_exclusion: 'false']], limits, metrics)
        assertThat res.result, is(Powerwaf.Result.MATCH)

        res = ctx.runRules(
                ['server.request.query': [excluded_key: 'true', activate_exclusion: 'true']], limits, metrics)
        assertThat res.result, is(Powerwaf.Result.OK)
    }

    @Test
    void 'rule toggling'() {
        def ruleSet = ARACHNI_ATOM_BLOCK

        ctx = Powerwaf.createContext('test', ruleSet)

        Map<String, Object> overrideSpec = [
                metadata: [
                        rules_version: '1.2.7'
                ],
                rules_override: [
                        [
                                rules_target: [
                                        [
                                                rule_id: 'arachni_rule'
                                        ]
                                ],
                                enabled: false
                        ]
                ]
        ]
        ctx.withCloseable {
            ctx = ctx.update('test2', overrideSpec)
            assertThat ctx.ruleSetInfo.rulesetVersion, is('1.2.7')
        }
        Powerwaf.ResultWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.OK)

        overrideSpec['rules_override'][0]['enabled'] = true
        ctx.withCloseable {
            ctx = ctx.update('test3', overrideSpec)
        }
        awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }

    @Test
    void 'custom rules'() {
        def ruleSet = ARACHNI_ATOM_BLOCK

        ctx = Powerwaf.createContext('test', ruleSet)

        Map<String, Object> customRules = [
            rules: [],
            custom_rules: [[
                 id: 'my rule',
                 name: 'My Rule',
                 tags: [
                        type: 'security_scanner',
                        category: 'attack_attempt'
                 ],
                 conditions: [[
                     parameters: [
                            inputs: [
                                [
                                    address: 'server.request.headers.no_cookies',
                                    key_path: ['user-agent']
                                ]],
                            regex: 'foobar'
                     ],
                     operator: 'match_regex'
        ]]]]]
        ctx.withCloseable {
            ctx = ctx.update('test2', customRules)
        }
        def awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.OK)
        awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'foobar']], limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }
}
