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
import org.junit.Test

import static Waf.ResultWithData
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.contains
import static org.hamcrest.Matchers.containsInAnyOrder
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.notNullValue

class BasicTests extends WafTestBase {

    @Test
    void 'the version is correct'() {
        assert Waf.version =~ Waf.LIB_VERSION
    }

    @Test
    void 'test running basic rule v1_0'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni']], limits, wafMetrics, builder)
        assertThat awd.result, is(Waf.Result.MATCH)

        def json = new JsonSlurper().parseText(awd.data)

        assert json[0].rule.id == 'arachni_rule'
        assert json[0].rule.name == 'Arachni'
        assert json[0].rule.tags == [type: 'arachni_detection']
        assert json[0].rule_matches[0]['operator'] == 'match_regex'
        assert json[0].rule_matches[0]['operator_value'] == 'Arachni'
        assert json[0].rule_matches[0]['parameters'][0].address == 'server.request.headers.no_cookies'
        assert json[0].rule_matches[0]['parameters'][0].key_path == ['user-agent']
        assert json[0].rule_matches[0]['parameters'][0].value == 'Arachni'
        assert json[0].rule_matches[0]['parameters'][0].highlight == ['Arachni']

        assert ruleSetInfo.rules.loaded == ['arachni_rule']
        assert ruleSetInfo.numConfigOK == 1
        assert ruleSetInfo.numConfigError == 0
        assert ruleSetInfo.allErrors == [:]
        assert ruleSetInfo.rulesetVersion == null
    }

    @Test
    void 'test running basic rule v2_1'() {
        def ruleSet = ARACHNI_ATOM_V2_1

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, wafMetrics,
                builder)
        assertThat awd.result, is(Waf.Result.MATCH)

        def json = new JsonSlurper().parseText(awd.data)

        assert json[0].rule.id == 'arachni_rule'
        assert json[0].rule.name == 'Arachni'
        assert json[0].rule.tags == [category: 'attack_attempt', type: 'security_scanner']
        assert json[0].rule_matches[0]['operator'] == 'match_regex'
        assert json[0].rule_matches[0]['operator_value'] == '^Arachni\\/v'
        assert json[0].rule_matches[0]['parameters'][0].address == 'server.request.headers.no_cookies'
        assert json[0].rule_matches[0]['parameters'][0].key_path == ['user-agent']
        assert json[0].rule_matches[0]['parameters'][0].value == 'Arachni/v1'
        assert json[0].rule_matches[0]['parameters'][0].highlight == ['Arachni/v']

        assert ruleSetInfo.numConfigOK == 1
        assert ruleSetInfo.numConfigError == 0
        assert ruleSetInfo.allErrors == [:]
        assert ruleSetInfo.rulesetVersion == '1.2.6'

        assert wafMetrics.totalRunTimeNs > 0
        assert wafMetrics.totalDdwafRunTimeNs > 0
        assert wafMetrics.totalRunTimeNs >= wafMetrics.totalDdwafRunTimeNs
    }

    @Test
    void 'test blocking action'() {
        def ruleSet = ARACHNI_ATOM_BLOCK

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, wafMetrics,
                builder)
        assertThat awd.result, is(Waf.Result.MATCH)
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

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, wafMetrics,
                builder)
        assertThat awd.result, is(Waf.Result.MATCH)
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
        def ruleSet = new JsonSlurper().parseText(JsonOutput.toJson(ARACHNI_ATOM_BLOCK))
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

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, wafMetrics,
                builder)
        assertThat awd.result, is(Waf.Result.MATCH)
        assertThat awd.actions.keySet(), containsInAnyOrder('aaaa', 'block_request', 'bbbb')
    }

    @Test
    void 'test actions with various types'() {
        def ruleSet = new JsonSlurper().parseText(JsonOutput.toJson(ARACHNI_ATOM_BLOCK))
        ruleSet.putAt('actions', [
                [
                        id: 'block',
                        parameters: [
                                status_code: 201,       // integer
                                type: 'auto',
                                grpc_status_code: '10', // string
                                enabled: true,          // boolean
                                test: 'false'           // string
                        ],
                        type: 'block_request'
                ]
        ])
        ruleSet['rules'][0]['on_match'] = ['block']

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, wafMetrics,
                builder)
        assertThat awd.result, is(Waf.Result.MATCH)
        assertThat awd.actions.keySet(), contains('block_request')

        assertThat awd.actions.get('block_request').type, is('auto')
        assertThat awd.actions.get('block_request').status_code, is('201')
        assertThat awd.actions.get('block_request').grpc_status_code, is('10')
        assertThat awd.actions.get('block_request').enabled, is('true')
        assertThat awd.actions.get('block_request').test, is('false')
    }

    @Test
    void 'test with array of string lists'() {
        def ruleSet = ARACHNI_ATOM_V1_0
        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        def data = [
            attack: ['o:1:"ee":1:{}'],
            PassWord: ['Arachni'],
        ]
        ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits, wafMetrics,
                builder)
        assertThat awd.result, is(Waf.Result.MATCH)
    }

    @Test
    void 'test with array'() {
        def ruleSet = ARACHNI_ATOM_V1_0

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        def data = ['foo', 'Arachni'] as String[]
        ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits, wafMetrics, builder)
        assertThat awd.result, is(Waf.Result.MATCH)
    }

    @Test
    void 'test null argument'() {
        def ruleSet = ARACHNI_ATOM_V1_0
        builder = new WafBuilder()

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        def data = [null, 'Arachni']
        ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits, wafMetrics, builder)
        assertThat awd.result, is(Waf.Result.MATCH)
    }

    @Test
    void 'test boolean arguments'() {
        def ruleSet = ARACHNI_ATOM_V1_0
        builder = new WafBuilder()

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        def data = [true, false, 'Arachni']
        ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits, wafMetrics, builder)
        assertThat awd.result, is(Waf.Result.MATCH)
    }

    @SuppressWarnings('EmptyClass')
    static class MyClass { }

    @Test
    void 'test unencodable arguments'() {
        def ruleSet = ARACHNI_ATOM_V1_0
        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        def data = [new MyClass(), 'Arachni']
        ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits, wafMetrics, builder)
        assertThat awd.result, is(Waf.Result.MATCH)
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
        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        ResultWithData res = Waf.runContext(['http.client_ip': '1.2.3.4'], limits, wafMetrics, builder)
        assertThat res.result, is(Waf.Result.OK)

        res = Waf.runContext(['usr.id': 'paco'], limits, wafMetrics, builder)
        assertThat res.result, is(Waf.Result.OK)

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
        ruleSetInfo = builder.addOrUpdateConfig('enyaX', [rules_data: newData])

        res = Waf.runContext(['http.client_ip': '1.2.3.4'], limits, wafMetrics, builder)
        assertThat res.result, is(Waf.Result.MATCH)

        res = Waf.runContext(['usr.id': 'paco'], limits, wafMetrics, builder)
        assertThat res.result, is(Waf.Result.MATCH)
        builder.removeConfig('enyaX')
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

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        ResultWithData res = Waf.runContext(['server.request.query': [excluded_key: 'true']], limits, wafMetrics,
                builder)
        assertThat res.result, is(Waf.Result.MATCH)

        res = Waf.runContext(
                ['server.request.query': [excluded_key: 'true', activate_exclusion: 'false']], limits, wafMetrics,
                builder)
        assertThat res.result, is(Waf.Result.MATCH)

        res = Waf.runContext(
                ['server.request.query': [excluded_key: 'true', activate_exclusion: 'true']], limits, wafMetrics,
                builder)
        assertThat res.result, is(Waf.Result.OK)
    }

    @Test
    void 'rule toggling'() {
        def ruleSet = ARACHNI_ATOM_BLOCK
        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

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
        ruleSetInfo = builder.addOrUpdateConfig('enyaD', overrideSpec)
        assertThat ruleSetInfo.rulesetVersion, is('1.2.7')

        Waf.ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, wafMetrics,
                builder)
        assertThat awd.result, is(Waf.Result.OK)

        overrideSpec['rules_override'][0]['enabled'] = true

        ruleSetInfo = builder.addOrUpdateConfig('enyaD', overrideSpec)
        awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, wafMetrics,
                builder)
        assertThat awd.result, is(Waf.Result.MATCH)
    }

    @Test
    void 'custom rules'() {
        def ruleSet = ARACHNI_ATOM_BLOCK
        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

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
        ruleSetInfo = builder.addOrUpdateConfig('enya', customRules)

        def awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, wafMetrics,
                builder)
        assertThat awd.result, is(Waf.Result.OK)
        awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': 'foobar']], limits, wafMetrics, builder)
        assertThat awd.result, is(Waf.Result.MATCH)
    }
}
