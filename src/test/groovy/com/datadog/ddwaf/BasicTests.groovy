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

class BasicTests implements WafTrait {

  @Test
  void 'the version is correct'() {
    assert Waf.version =~ Waf.LIB_VERSION
  }

  @Test
  void 'test running basic rule v1_0'() {
    def ruleSet = ARACHNI_ATOM_V1_0

    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    assert wafDiagnostics.rules.loaded == ['arachni_rule']
    assert wafDiagnostics.numConfigOK == 1
    assert wafDiagnostics.numConfigError == 0
    assert wafDiagnostics.allErrors == [:]
    assert wafDiagnostics.rulesetVersion == null

    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    final params = ['server.request.headers.no_cookies': ['user-agent': 'Arachni']]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)

    def json = slurper.parseText(res.data)

    assert json[0].rule.id == 'arachni_rule'
    assert json[0].rule.name == 'Arachni'
    assert json[0].rule.tags == [type: 'arachni_detection']
    assert json[0].rule_matches[0]['operator'] == 'match_regex'
    assert json[0].rule_matches[0]['operator_value'] == 'Arachni'
    assert json[0].rule_matches[0]['parameters'][0].address == 'server.request.headers.no_cookies'
    assert json[0].rule_matches[0]['parameters'][0].key_path == ['user-agent']
    assert json[0].rule_matches[0]['parameters'][0].value == 'Arachni'
    assert json[0].rule_matches[0]['parameters'][0].highlight == ['Arachni']
  }

  @Test
  void 'test running basic rule v2_1'() {
    def ruleSet = ARACHNI_ATOM_V2_1

    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    assert wafDiagnostics.numConfigOK == 1
    assert wafDiagnostics.numConfigError == 0
    assert wafDiagnostics.allErrors == [:]
    assert wafDiagnostics.rulesetVersion == '1.2.6'

    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    final params = ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)

    def json = slurper.parseText(res.data)

    assert json[0].rule.id == 'arachni_rule'
    assert json[0].rule.name == 'Arachni'
    assert json[0].rule.tags == [category: 'attack_attempt', type: 'security_scanner']
    assert json[0].rule_matches[0]['operator'] == 'match_regex'
    assert json[0].rule_matches[0]['operator_value'] == '^Arachni\\/v'
    assert json[0].rule_matches[0]['parameters'][0].address == 'server.request.headers.no_cookies'
    assert json[0].rule_matches[0]['parameters'][0].key_path == ['user-agent']
    assert json[0].rule_matches[0]['parameters'][0].value == 'Arachni/v1'
    assert json[0].rule_matches[0]['parameters'][0].highlight == ['Arachni/v']

    assert metrics.totalRunTimeNs > 0
    assert metrics.totalDdwafRunTimeNs > 0
    assert metrics.totalRunTimeNs >= metrics.totalDdwafRunTimeNs
  }

  @Test
  void 'test blocking action'() {
    def ruleSet = ARACHNI_ATOM_BLOCK

    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    final params = ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
    assertThat res.result, is(Waf.Result.MATCH)
    assertThat res.actions.size(), is(1)
    assertThat res.actions.keySet(), hasItem('block_request')
    assertThat res.actions.get('block_request').type, is('auto')
    assertThat res.actions.get('block_request').status_code, is('403')
    assertThat res.actions.get('block_request').grpc_status_code, is('10')
  }

  @Test
  void 'test built-in actions'() {
    def ruleSet = ARACHNI_ATOM_V2_1
    ruleSet['rules'][0]['on_match'] = ['block', 'stack_trace', 'extract_schema']

    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    final params = ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
    assertThat res.actions.size(), is(3)

    // block action
    assertThat res.actions.keySet(), hasItem('block_request')
    assertThat res.actions.get('block_request').type, is('auto')
    assertThat res.actions.get('block_request').status_code, is('403')
    assertThat res.actions.get('block_request').grpc_status_code, is('10')

    // stack_trace action
    assertThat res.actions.keySet(), hasItem('generate_stack')
    assertThat res.actions.get('generate_stack').stack_id, is(notNullValue())

    // extract_schema action
    assertThat res.actions.keySet(), hasItem('generate_schema')
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

    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    final params = ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
    assertThat res.actions.keySet(), containsInAnyOrder('aaaa', 'block_request', 'bbbb')
  }

  @Test
  void 'test actions with various types'() {
    def ruleSet = slurper.parseText(JsonOutput.toJson(ARACHNI_ATOM_BLOCK))
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

    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    final params = ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
    assertThat res.actions.keySet(), contains('block_request')

    assertThat res.actions.get('block_request').type, is('auto')
    assertThat res.actions.get('block_request').status_code, is('201')
    assertThat res.actions.get('block_request').grpc_status_code, is('10')
    assertThat res.actions.get('block_request').enabled, is('true')
    assertThat res.actions.get('block_request').test, is('false')
  }

  @Test
  void 'test with array of string lists'() {
    def ruleSet = ARACHNI_ATOM_V1_0
    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    def data = [
      attack: ['o:1:"ee":1:{}'],
      PassWord: ['Arachni'],
    ]
    final params = ['server.request.headers.no_cookies': ['user-agent': data]]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
  }

  @Test
  void 'test with array'() {
    def ruleSet = ARACHNI_ATOM_V1_0
    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    def data = ['foo', 'Arachni'] as String[]
    final params = ['server.request.headers.no_cookies': ['user-agent': data]]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
  }

  @Test
  void 'test null argument'() {
    def ruleSet = ARACHNI_ATOM_V1_0
    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    def data = [null, 'Arachni']
    final params = ['server.request.headers.no_cookies': ['user-agent': data]]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
  }

  @Test
  void 'test boolean arguments'() {
    def ruleSet = ARACHNI_ATOM_V1_0
    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    def data = [true, false, 'Arachni']
    final params = ['server.request.headers.no_cookies': ['user-agent': data]]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
  }

  @SuppressWarnings('EmptyClass')
  static class MyClass { }

  @Test
  void 'test unencodable arguments'() {
    def ruleSet = ARACHNI_ATOM_V1_0
    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    def data = [new MyClass(), 'Arachni']
    final params = ['server.request.headers.no_cookies': ['user-agent': data]]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
  }

  @Test
  void 'can retrieve used addresses'() {
    def ruleSet = ARACHNI_ATOM_V2_1
    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    assertThat handle.knownAddresses as List, contains('server.request.headers.no_cookies')
  }

  @Test
  void 'can retrieve used actions'() {
    def ruleSet = ARACHNI_ATOM_BLOCK
    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    assertThat handle.knownActions as List,
      containsInAnyOrder('block_request', 'generate_stack', 'redirect_request')
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
    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet as Map<String, Object>)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    assert handle.knownAddresses.length == 0

    final params = ['server.request.headers.no_cookies': ['user-agent': 'Arachni']]
    ResultWithData res = context.run(params, limits, metrics)
    assertThat res.result, is(Waf.Result.OK)
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
    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    ResultWithData res = context.run(['http.client_ip': '1.2.3.4'], limits, metrics)
    assertThat res.result, is(Waf.Result.OK)

    res = context.run(['usr.id': 'paco'], limits, metrics)
    assertThat res.result, is(Waf.Result.OK)
    context.close()
    handle.close()

    def newData = [
      [
        id: 'ip_data',
        type: 'ip_with_expiration',
        data: [[
            value: '1.2.3.4',
            expiration: '0',
          ]]
      ],
      [
        id: 'usr_data',
        type: 'data_with_expiration',
        data: [[
            value: 'paco',
            expiration: '0',
          ]]

      ]
    ]
    wafDiagnostics = builder.addOrUpdateConfig('testX', [rules_data: newData])
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    res = context.run(['http.client_ip': '1.2.3.4'], limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)

    res = context.run(['usr.id': 'paco'], limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
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

    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    ResultWithData res = context.run(['server.request.query': [excluded_key: 'true']], limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
    context.close()

    context = new WafContext(handle)
    res = context.run(
      ['server.request.query': [excluded_key: 'true', activate_exclusion: 'false']], limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
    context.close()

    context = new WafContext(handle)
    res = context.run(
      ['server.request.query': [excluded_key: 'true', activate_exclusion: 'true']], limits, metrics)
    assertThat res.result, is(Waf.Result.OK)
  }

  @Test
  void 'test exclusion data'() {
    final suspiciousIp = '34.65.27.85'
    final userAgent = 'Arachni/v1.5.1'
    final ruleSet = slurper.parseText(JsonOutput.toJson(ARACHNI_ATOM_V2_1))
    ruleSet.rules[0].remove('on_match') // other tests are modifying the rule
    ruleSet.putAt('exclusions', [
      [
        id        : 'exc-000-001',
        on_match  : 'block',
        conditions: [
          [
            operator  : 'ip_match',
            parameters: [
              data  : 'suspicious_ips_data_id',
              inputs: [[address: 'http.client_ip']]]
          ]
        ],
      ]
    ])
    builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    ResultWithData res = context.run(
      [
        'http.client_ip'                   : suspiciousIp,
        'server.request.headers.no_cookies': ['user-agent': [userAgent]]
      ],
      limits,
      metrics
      )
    assertThat res.result, is(Waf.Result.MATCH)
    assertThat res.actions.size(), is(0)

    def newData = [
      [
        id  : 'suspicious_ips_data_id',
        type: 'ip_with_expiration',
        data: [[value: suspiciousIp, expiration: 0]]
      ]
    ]

    builder.addOrUpdateConfig('test2', [exclusion_data: newData])
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    res = context.run(
      [
        'http.client_ip'                   : suspiciousIp,
        'server.request.headers.no_cookies': ['user-agent': [userAgent]]
      ],
      limits,
      metrics
      )
    assertThat res.result, is(Waf.Result.MATCH)
    assertThat res.actions.size(), is(1)
    assertThat res.actions.get('block_request'), notNullValue()
  }

  @Test
  void 'rule toggling'() {
    def ruleSet = ARACHNI_ATOM_BLOCK
    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)

    Map<String, Object> overrideSpec = [
      metadata: [
        rules_version: '1.2.7'
      ],
      rules_override: [
        [
          rules_target: [[
              rule_id: 'arachni_rule'
            ]],
          enabled: false
        ]
      ]
    ]
    wafDiagnostics = builder.addOrUpdateConfig('testD', overrideSpec)
    assertThat wafDiagnostics.rulesetVersion, is('1.2.7')
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    Waf.ResultWithData res = context.run(
      ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
    assertThat res.result, is(Waf.Result.OK)
    context.close()
    handle.close()

    overrideSpec['rules_override'][0]['enabled'] = true
    wafDiagnostics = builder.addOrUpdateConfig('testD', overrideSpec)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    res = context.run(
      ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
  }

  @Test
  void 'custom rules'() {
    def ruleSet = ARACHNI_ATOM_BLOCK
    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)

    Map<String, Object> customRules = [
      rules: [],
      custom_rules: [
        [
          id: 'my rule',
          name: 'My Rule',
          tags: [
            type: 'security_scanner',
            category: 'attack_attempt'
          ],
          conditions: [
            [
              parameters: [
                inputs: [
                  [
                    address: 'server.request.headers.no_cookies',
                    key_path: ['user-agent']
                  ]
                ],
                regex: 'foobar'
              ],
              operator: 'match_regex'
            ]
          ]]
      ]]
    wafDiagnostics = builder.addOrUpdateConfig('test', customRules)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    def res = context.run(
      ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
    assertThat res.result, is(Waf.Result.OK)
    context.close()

    context = new WafContext(handle)
    res = context.run(
      ['server.request.headers.no_cookies': ['user-agent': 'foobar']], limits, metrics)
    assertThat res.result, is(Waf.Result.MATCH)
  }
}

