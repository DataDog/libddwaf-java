/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

package com.datadog.ddwaf

import groovy.json.JsonSlurper
import org.junit.Test

class RulesCompatTest implements WafTrait {

  @Test
  void 'test rules_compat with duplicate rules'() {
    def rulesetWithDuplicates = [
      version: '2.1',
      metadata: [
        rules_version: '1.2.7'
      ],
      rules: [
        [
          id: 'duplicate_rule',
          name: 'Duplicate Rule in Regular',
          tags: [
            type: 'security_scanner',
            category: 'attack_attempt'
          ],
          conditions: [
            [
              operator: 'match_regex',
              parameters: [
                inputs: [
                  [
                    address: 'server.request.uri.raw'
                  ]
                ],
                regex: '.*duplicate.*'
              ]
            ]
          ],
          transformers: [],
          on_match: ['block']
        ]
      ],
      rules_compat: [
        [
          id: 'duplicate_rule',
          name: 'Duplicate Rule in Compat',
          tags: [
            type: 'security_scanner',
            category: 'attack_attempt'
          ],
          conditions: [
            [
              operator: 'match_regex',
              parameters: [
                inputs: [
                  [
                    address: 'server.request.uri.raw'
                  ]
                ],
                regex: '.*duplicate.*'
              ]
            ]
          ],
          transformers: [],
          on_match: ['block']
        ]
      ]
    ]

    // Add configuration and build WAF
    wafDiagnostics = builder.addOrUpdateConfig('test', rulesetWithDuplicates)
    assert wafDiagnostics.numConfigOK == 1
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Test that the rule still works despite duplicates
    def params = [
      'server.request.uri.raw': 'http://example.com/duplicate'
    ]
    def result = context.run(params, limits, metrics)

    // Should still match and block
    assert result.result == Waf.Result.MATCH

    // Parse JSON result to verify rule match
    def jsonResult = new JsonSlurper().parseText(result.data)
    assert jsonResult.any { it.rule?.id == 'duplicate_rule' }
  }

  @Test
  void 'test rules_compat with invalid configuration'() {
    def invalidRuleset = [
      version: '2.1',
      metadata: [
        rules_version: '1.2.7'
      ],
      rules: [
        [
          id: 'valid_rule',
          name: 'Valid Rule',
          tags: [
            type: 'security_scanner',
            category: 'attack_attempt'
          ],
          conditions: [
            [
              operator: 'match_regex',
              parameters: [
                inputs: [
                  [
                    address: 'server.request.uri.raw'
                  ]
                ],
                regex: '.*valid.*'
              ]
            ]
          ],
          transformers: [],
          on_match: ['block']
        ]
      ],
      rules_compat: [
        // Invalid: should be array of rules, not a single rule object
        id: 'invalid_rule',
        name: 'Invalid Rule Structure'
      ]
    ]

    // Add configuration and build WAF
    wafDiagnostics = builder.addOrUpdateConfig('test', invalidRuleset)
    assert wafDiagnostics.numConfigOK == 1
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Test that valid rules still work
    def params = [
      'server.request.uri.raw': 'http://example.com/valid'
    ]
    def result = context.run(params, limits, metrics)

    // Valid rule should still work
    assert result.result == Waf.Result.MATCH

    // Parse JSON result to verify rule match
    def jsonResult = new JsonSlurper().parseText(result.data)
    assert jsonResult.any { it.rule?.id == 'valid_rule' }
  }

  @Test
  void 'test rules_compat rule produces attributes'() {
    def rulesetWithCompatAttributes = [
      version: '2.1',
      metadata: [
        rules_version: '1.2.7'
      ],
      rules: [
        [
          id: 'rule1',
          name: 'rule1',
          tags: [
            type: 'flow1',
            category: 'category1',
            confidence: '1'
          ],
          conditions: [
            [
              operator: 'match_regex',
              parameters: [
                inputs: [[ address: 'value1' ]],
                regex: '^rule1'
              ]
            ]
          ],
          transformers: [],
          on_match: ['block']
        ]
      ],
      rules_compat: [
        [
          id: 'rule2',
          name: 'rule2',
          tags: [
            type: 'flow2',
            category: 'category2'
          ],
          conditions: [
            [
              operator: 'match_regex',
              parameters: [
                inputs: [[ address: 'value2' ]],
                regex: '^rule2'
              ]
            ]
          ],
          output: [
            event: true,
            keep: false,
            attributes: [
              'result.rule2': [ value: 'something' ]
            ]
          ],
          on_match: ['block']
        ]
      ]
    ]

    wafDiagnostics = builder.addOrUpdateConfig('test', rulesetWithCompatAttributes)
    assert wafDiagnostics.numConfigOK == 1
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Test regular rule
    def params1 = [ 'value1': 'rule1' ]
    def result1 = context.run(params1, limits, metrics)
    assert result1.result == Waf.Result.MATCH

    def jsonResult1 = new JsonSlurper().parseText(result1.data)
    assert jsonResult1.any { it.rule?.id == 'rule1' }

    // Test rules_compat rule with attributes
    def params2 = [ 'value2': 'rule2' ]
    def result2 = context.run(params2, limits, metrics)
    assert result2.result == Waf.Result.MATCH

    def jsonResult2 = new JsonSlurper().parseText(result2.data)
    assert jsonResult2.any { it.rule?.id == 'rule2' }

    // Assert attributes from rules_compat rule
    assert result2.attributes instanceof Map
    assert result2.attributes['result.rule2'] == 'something'
  }

  @Test
  void 'test rules_compat rule produces numeric attributes'() {
    def rulesetWithNumericAttributes = [
      version: '2.1',
      metadata: [
        rules_version: '1.2.7'
      ],
      rules: [
        [
          id: 'rule1',
          name: 'rule1',
          tags: [
            type: 'flow1',
            category: 'category1'
          ],
          conditions: [
            [
              operator: 'match_regex',
              parameters: [
                inputs: [[ address: 'value1' ]],
                regex: '^rule1'
              ]
            ]
          ],
          transformers: [],
          on_match: ['block']
        ]
      ],
      rules_compat: [
        [
          id: 'rule2',
          name: 'rule2',
          tags: [
            type: 'flow2',
            category: 'category2'
          ],
          conditions: [
            [
              operator: 'match_regex',
              parameters: [
                inputs: [[ address: 'value2' ]],
                regex: '^rule2'
              ]
            ]
          ],
          output: [
            event: true,
            keep: false,
            attributes: [
              'rule2.int64': [ value: -200 ],
              'rule2.uint64': [ value: 200 ],
              'rule2.double': [ value: 200.22 ],
              'rule2.bool': [ value: true ]
            ]
          ],
          on_match: ['block']
        ]
      ]
    ]

    wafDiagnostics = builder.addOrUpdateConfig('test', rulesetWithNumericAttributes)
    assert wafDiagnostics.numConfigOK == 1
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Test rules_compat rule with numeric attributes
    def params = [ 'value2': 'rule2' ]
    def result = context.run(params, limits, metrics)
    assert result.result == Waf.Result.MATCH

    def jsonResult = new JsonSlurper().parseText(result.data)
    assert jsonResult.any { it.rule?.id == 'rule2' }

    // Assert numeric attributes from rules_compat rule
    assert result.attributes instanceof Map
    assert result.attributes['rule2.int64'] == -200L
    assert result.attributes['rule2.uint64'] == 200L
    assert result.attributes['rule2.double'] == 200.22d
    assert result.attributes['rule2.bool'] == true
  }

  @Test
  void 'test rules_compat rule produces multiple numeric attributes'() {
    def rulesetWithMultipleNumericAttributes = [
      version: '2.1',
      metadata: [
        rules_version: '1.2.7'
      ],
      rules: [
        [
          id: 'rule1',
          name: 'rule1',
          tags: [
            type: 'flow1',
            category: 'category1'
          ],
          conditions: [
            [
              operator: 'match_regex',
              parameters: [
                inputs: [[ address: 'value1' ]],
                regex: '^rule1'
              ]
            ]
          ],
          transformers: [],
          on_match: ['block']
        ]
      ],
      rules_compat: [
        [
          id: 'rule2',
          name: 'rule2',
          tags: [
            type: 'flow2',
            category: 'category2'
          ],
          conditions: [
            [
              operator: 'match_regex',
              parameters: [
                inputs: [[ address: 'value2' ]],
                regex: '^rule2'
              ]
            ]
          ],
          output: [
            event: true,
            keep: false,
            attributes: [
              'attr1.int64': [ value: -100 ],
              'attr2.uint64': [ value: 100 ],
              'attr3.double': [ value: 100.5 ],
              'attr4.bool': [ value: true ],
              'attr5.int64': [ value: -200 ],
              'attr6.uint64': [ value: 200 ],
              'attr7.double': [ value: 200.75 ],
              'attr8.bool': [ value: false ],
              'attr9.int64': [ value: -300 ],
              'attr10.uint64': [ value: 300 ]
            ]
          ],
          on_match: ['block']
        ]
      ]
    ]

    wafDiagnostics = builder.addOrUpdateConfig('test', rulesetWithMultipleNumericAttributes)
    assert wafDiagnostics.numConfigOK == 1
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Test rules_compat rule with multiple numeric attributes
    def params = [ 'value2': 'rule2' ]
    def result = context.run(params, limits, metrics)
    assert result.result == Waf.Result.MATCH

    def jsonResult = new JsonSlurper().parseText(result.data)
    assert jsonResult.any { it.rule?.id == 'rule2' }

    // Assert multiple numeric attributes from rules_compat rule
    assert result.attributes instanceof Map
    assert result.attributes.size() == 10

    // Check all numeric attributes
    assert result.attributes['attr1.int64'] == -100L
    assert result.attributes['attr2.uint64'] == 100L
    assert result.attributes['attr3.double'] == 100.5d
    assert result.attributes['attr4.bool'] == true
    assert result.attributes['attr5.int64'] == -200L
    assert result.attributes['attr6.uint64'] == 200L
    assert result.attributes['attr7.double'] == 200.75d
    assert result.attributes['attr8.bool'] == false
    assert result.attributes['attr9.int64'] == -300L
    assert result.attributes['attr10.uint64'] == 300L
  }

  @Test
  void 'test rules_compat rule produces many numeric attributes'() {
    def rulesetWithManyNumericAttributes = MANY_NUMERIC_ATTRIBUTES_RULESET

    wafDiagnostics = builder.addOrUpdateConfig('test', rulesetWithManyNumericAttributes)
    assert wafDiagnostics.numConfigOK == 1
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Test rules_compat rule with many numeric attributes
    def params = [ 'value2': 'rule2' ]
    def result = context.run(params, limits, metrics)
    assert result.result == Waf.Result.MATCH

    def jsonResult = new JsonSlurper().parseText(result.data)
    assert jsonResult.any { it.rule?.id == 'rule2' }

    // Assert many numeric attributes from rules_compat rule
    assert result.attributes instanceof Map
    assert result.attributes.size() == 20

    // Check a sample of numeric attributes
    assert result.attributes['metric1.int64'] == -1000L
    assert result.attributes['metric2.uint64'] == 1000L
    assert result.attributes['metric3.double'] == 1000.123d
    assert result.attributes['metric4.bool'] == true
    assert result.attributes['metric10.uint64'] == 3000L
    assert result.attributes['metric15.double'] == 4000.012d
    assert result.attributes['metric16.bool'] == false
    assert result.attributes['metric20.bool'] == true
  }

  @Test
  void 'test rules_compat rule produces mixed attribute types'() {
    def rulesetWithMixedAttributes = MIXED_ATTRIBUTES_RULESET

    wafDiagnostics = builder.addOrUpdateConfig('test', rulesetWithMixedAttributes)
    assert wafDiagnostics.numConfigOK == 1
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Test rules_compat rule with mixed attribute types
    def params = [ 'value2': 'rule2' ]
    def result = context.run(params, limits, metrics)
    assert result.result == Waf.Result.MATCH

    def jsonResult = new JsonSlurper().parseText(result.data)
    assert jsonResult.any { it.rule?.id == 'rule2' }

    // Assert mixed attributes from rules_compat rule
    assert result.attributes instanceof Map
    assert result.attributes.size() == 10

    // Check string attributes
    assert result.attributes['string1'] == 'hello world'
    assert result.attributes['string2'] == 'test string'
    assert result.attributes['string3'] == 'special chars: !@#$%^&*()'
    assert result.attributes['string4'] == 'unicode: 🚀🔥💯'
    assert result.attributes['string5'] == 'empty string test'

    // Check long attributes
    assert result.attributes['long1'] == -5000L
    assert result.attributes['long2'] == 10000L
    assert result.attributes['long3'] == 999999999L

    // Check double attributes
    assert result.attributes['double1'] == 123.456d
    assert result.attributes['double2'] == -789.012d
  }

  @Test
  void 'test waf handles array input parameters'() {
    def rulesetWithArrayInput = [
      version: '2.1',
      metadata: [ rules_version: '1.2.7' ],
      rules: [
        [
          id: 'array_rule',
          name: 'Array Rule',
          tags: [ type: 'array', category: 'test' ],
          conditions: [
            [
              operator: 'match_regex',
              parameters: [
                inputs: [[ address: 'input_array' ]],
                regex: '^matchme'
              ]
            ]
          ],
          transformers: [],
          on_match: ['block']
        ]
      ]
    ]

    wafDiagnostics = builder.addOrUpdateConfig('test', rulesetWithArrayInput)
    assert wafDiagnostics.numConfigOK == 1
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Test with array input containing a matching element
    def params = [ 'input_array': ['foo', 'matchme123', 'bar'] ]
    def result = context.run(params, limits, metrics)
    assert result.result == Waf.Result.MATCH
    def jsonResult = new JsonSlurper().parseText(result.data)
    assert jsonResult.any { it.rule?.id == 'array_rule' }

    // Test with array input containing no matching element
    def params2 = [ 'input_array': ['foo', 'baz', 'qux'] ]
    def result2 = context.run(params2, limits, metrics)
    assert result2.result == Waf.Result.OK
  }

  @Test
  void 'test waf handles complex attribute structures'() {
    def rulesetWithComplexAttributes = [
      version: '2.1',
      metadata: [ rules_version: '1.2.7' ],
      rules: [
        [
          id: 'complex_attr_rule',
          name: 'Complex Attribute Rule',
          tags: [ type: 'complex', category: 'test' ],
          conditions: [
            [
              operator: 'match_regex',
              parameters: [
                inputs: [
                  [
                    address: 'user_data',
                    key_path: ['profile', 'settings', 'preferences']
                  ],
                  [
                    address: 'request_data',
                    key_path: ['headers', 'user-agent'],
                    transformers: ['lowercase', 'normalize_path']
                  ]
                ],
                regex: 'malicious'
              ]
            ]
          ],
          transformers: [],
          on_match: ['block']
        ]
      ]
    ]

    wafDiagnostics = builder.addOrUpdateConfig('test', rulesetWithComplexAttributes)
    assert wafDiagnostics.numConfigOK == 1
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Test with complex nested data structure
    def params = [
      'user_data': [
        profile: [
          settings: [
            preferences: 'malicious_content_here'
          ]
        ]
      ],
      'request_data': [
        headers: [
          'user-agent': 'Mozilla/5.0 malicious_browser'
        ]
      ]
    ]

    def result = context.run(params, limits, metrics)
    assert result.result == Waf.Result.MATCH
    def jsonResult = new JsonSlurper().parseText(result.data)
    assert jsonResult.any { it.rule?.id == 'complex_attr_rule' }

    // Test with no matches in the complex structure
    def params2 = [
      'user_data': [
        profile: [
          settings: [
            preferences: 'safe_content'
          ]
        ]
      ],
      'request_data': [
        headers: [
          'user-agent': 'Mozilla/5.0 safe_browser'
        ]
      ]
    ]

    def result2 = context.run(params2, limits, metrics)
    assert result2.result == Waf.Result.OK
  }

  @Test
  void 'test trace tagging with debug logging'() {
    def rulesetWithTraceTagging = TRACE_TAGGING_RULESET

    wafDiagnostics = builder.addOrUpdateConfig('test', rulesetWithTraceTagging)

    // Check if configuration was accepted
    assert wafDiagnostics.numConfigOK == 1, "WAF configuration was not accepted. numConfigOK = ${wafDiagnostics?.numConfigOK}"

    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Test with input that should match the rule
    def params = [
      'server.request.headers.no_cookies': [
        'user-agent': 'TraceTagging/v1'
      ]
    ]

    def result = context.run(params, limits, metrics)
    assert result.result == Waf.Result.MATCH

    // Since the rule has event: false and keep: false, result.data will be null
    // We only care about the attributes, not the events

    // Assert that both attributes are present
    assert result.attributes.containsKey('_dd.appsec.trace.integer'), 'Missing _dd.appsec.trace.integer attribute'
    assert result.attributes.containsKey('_dd.appsec.trace.agent'), 'Missing _dd.appsec.trace.agent attribute'

    // Assert the values
    assert result.attributes['_dd.appsec.trace.integer'] == 662607015L
    assert result.attributes['_dd.appsec.trace.agent'] == 'TraceTagging/v1'
  }

  private static final Map MANY_NUMERIC_ATTRIBUTES_RULESET = [
    version: '2.1',
    metadata: [
      rules_version: '1.2.7'
    ],
    rules: [
      [
        id: 'rule1',
        name: 'rule1',
        tags: [
          type: 'flow1',
          category: 'category1'
        ],
        conditions: [
          [
            operator: 'match_regex',
            parameters: [
              inputs: [[ address: 'value1' ]],
              regex: '^rule1'
            ]
          ]
        ],
        transformers: [],
        on_match: ['block']
      ]
    ],
    rules_compat: [
      [
        id: 'rule2',
        name: 'rule2',
        tags: [
          type: 'flow2',
          category: 'category2'
        ],
        conditions: [
          [
            operator: 'match_regex',
            parameters: [
              inputs: [[ address: 'value2' ]],
              regex: '^rule2'
            ]
          ]
        ],
        output: [
          event: true,
          keep: false,
          attributes: [
            'metric1.int64': [ value: -1000 ],
            'metric2.uint64': [ value: 1000 ],
            'metric3.double': [ value: 1000.123 ],
            'metric4.bool': [ value: true ],
            'metric5.int64': [ value: -2000 ],
            'metric6.uint64': [ value: 2000 ],
            'metric7.double': [ value: 2000.456 ],
            'metric8.bool': [ value: false ],
            'metric9.int64': [ value: -3000 ],
            'metric10.uint64': [ value: 3000 ],
            'metric11.double': [ value: 3000.789 ],
            'metric12.bool': [ value: true ],
            'metric13.int64': [ value: -4000 ],
            'metric14.uint64': [ value: 4000 ],
            'metric15.double': [ value: 4000.012 ],
            'metric16.bool': [ value: false ],
            'metric17.int64': [ value: -5000 ],
            'metric18.uint64': [ value: 5000 ],
            'metric19.double': [ value: 5000.345 ],
            'metric20.bool': [ value: true ]
          ]
        ],
        on_match: ['block']
      ]
    ]
  ]

  private static final Map MIXED_ATTRIBUTES_RULESET = [
    version: '2.1',
    metadata: [
      rules_version: '1.2.7'
    ],
    rules: [
      [
        id: 'rule1',
        name: 'rule1',
        tags: [
          type: 'flow1',
          category: 'category1'
        ],
        conditions: [
          [
            operator: 'match_regex',
            parameters: [
              inputs: [[ address: 'value1' ]],
              regex: '^rule1'
            ]
          ]
        ],
        transformers: [],
        on_match: ['block']
      ]
    ],
    rules_compat: [
      [
        id: 'rule2',
        name: 'rule2',
        tags: [
          type: 'flow2',
          category: 'category2'
        ],
        conditions: [
          [
            operator: 'match_regex',
            parameters: [
              inputs: [[ address: 'value2' ]],
              regex: '^rule2'
            ]
          ]
        ],
        output: [
          event: true,
          keep: false,
          attributes: [
            // 5 string attributes
            'string1': [ value: 'hello world' ],
            'string2': [ value: 'test string' ],
            'string3': [ value: 'special chars: !@#$%^&*()' ],
            'string4': [ value: 'unicode: 🚀🔥💯' ],
            'string5': [ value: 'empty string test' ],

            // 3 long attributes
            'long1': [ value: -5000 ],
            'long2': [ value: 10000 ],
            'long3': [ value: 999999999 ],

            // 2 double attributes
            'double1': [ value: 123.456 ],
            'double2': [ value: -789.012 ]
          ]
        ],
        on_match: ['block']
      ]
    ]
  ]

  private static final Map TRACE_TAGGING_RULESET = [
    version: '2.1',
    metadata: [
      rules_version: '1.2.7'
    ],
    rules: [
      [
        id: 'arachni_rule',
        name: 'Arachni',
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
              regex: '^Arachni\\/v'
            ],
            operator: 'match_regex'
          ]
        ],
        transformers: [],
        on_match: ['block']
      ]
    ],
    rules_compat: [
      [
        id: 'ttr-000-001',
        name: 'Trace Tagging Rule: Attributes, No Keep, No Event',
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
              regex: '^TraceTagging\\/v1'
            ],
            operator: 'match_regex'
          ]
        ],
        output: [
          event: false,
          keep: false,
          attributes: [
            '_dd.appsec.trace.integer': [
              value: 662607015
            ],
            '_dd.appsec.trace.agent': [
              value: 'TraceTagging/v1'
            ]
          ]
        ],
        on_match: []
      ]
    ]
  ]
}
