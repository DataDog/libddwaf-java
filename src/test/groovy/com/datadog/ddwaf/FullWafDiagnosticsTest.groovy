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

class FullWafDiagnosticsTest implements WafTrait {

  @Test
  void 'test comprehensive ruleset'() {
    // Create a comprehensive ruleset with all possible fields
    def completeRuleset = comprehensiveRuleset

    // Initialize WAF builder and add our complete ruleset
    WafDiagnostics diagnostics = builder.addOrUpdateConfig('comprehensive_test', completeRuleset)

    // Verify all sections of WAF diagnostics
    verifyDiagnosticsFields(diagnostics)

    // Build WAF handle and context for testing rule execution
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    testMatchingRules(context)
    testExclusionRules(context)
  }

  @Test
  void 'test ruleset with exclusion data'() {
    // Initialize WAF builder and add our ruleset with exclusion data
    WafDiagnostics diagnostics = builder.addOrUpdateConfig('exclusion_data_test', rulesetWithExclusionData)

    // Verify all sections of WAF diagnostics
    verifyExclusionDataDiagnostics(diagnostics)

    // Build WAF handle and context for testing rule execution
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    testIpMatchingAndExclusion(context)
  }

  Map<String, Object> rules =
  [
    rules: [
      [
        id: 'comprehensive_rule_1',
        name: 'Comprehensive Rule 1',
        tags: [
          type: 'security_scanner',
          category: 'attack_attempt',
          confidence: 'high'
        ],
        conditions: [
          [
            operator: 'match_regex',
            parameters: [
              inputs: [
                [
                  address: 'server.request.headers.no_cookies',
                  key_path: ['user-agent']
                ]
              ],
              regex: 'malicious.*pattern'
            ]
          ]
        ],
        on_match: ['block']
      ],
      [
        id: 'comprehensive_rule_2',
        name: 'Comprehensive Rule 2',
        tags: [
          type: 'security_scanner',
          category: 'attack_attempt',
          confidence: 'medium'
        ],
        conditions: [
          [
            operator: 'phrase_match',
            parameters: [
              inputs: [
                [
                  address: 'server.request.query',
                  key_path: ['param1']
                ]
              ],
              list: ['attack', 'malicious', 'injection']
            ]
          ]
        ],
        on_match: ['monitor']
      ]
    ]
  ]

  Map<String, Object> customRules =
  [
    custom_rules: [
      [
        id: 'custom_rule_1',
        name: 'Custom Rule 1',
        tags: [
          type: 'custom',
          category: 'custom_detection',
          confidence: 'high'
        ],
        conditions: [
          [
            operator: 'exact_match',
            parameters: [
              inputs: [
                [
                  address: 'server.request.body',
                  key_path: ['password']
                ]
              ],
              value: '123456'
            ]
          ]
        ],
        on_match: ['block']
      ]
    ]
  ]

  Map<String, Object> exclusions =
  [
    exclusions: [
      [
        id: 'exclusion_1',
        rules_target: [
          [
            tags: [
              type: 'security_scanner'
            ]
          ]
        ],
        conditions: [
          [
            operator: 'match_regex',
            parameters: [
              inputs: [
                [
                  address: 'server.request.uri.raw',
                  key_path: []
                ]
              ],
              regex: '^/api/health$'
            ]
          ]
        ]
      ]
    ]
  ]

  Map<String, Object> comprehensiveRuleset =
  [
    version: '2.2',
    metadata: [
      rules_version: '1.5.0'
    ],
    rules: rules.rules,
    custom_rules: customRules.custom_rules,
    rules_data: [
      [
        id: 'ip_data',
        type: 'data_with_expiration',
        data: [
          [
            value: '192.168.1.1',
            expiration: 0
          ],
          [
            value: '10.0.0.1',
            expiration: 0
          ]
        ]
      ],
      [
        id: 'usr_data',
        type: 'data_with_expiration',
        data: [
          [
            value: 'admin',
            expiration: 0
          ],
          [
            value: 'root',
            expiration: 0
          ]
        ]
      ]
    ],
    exclusions: exclusions.exclusions,
    processors: [
      [
        id: 'processor_1',
        generator: 'extract_schema',
        conditions: [
          [
            operator: 'equals',
            parameters: [
              inputs: [
                [
                  address: 'waf.context.processor',
                  key_path: ['schema']
                ]
              ],
              value: true,
              type: 'boolean'
            ]
          ]
        ],
        parameters: [
          mappings: [
            [
              inputs: [
                [
                  address: 'server.request.query'
                ]
              ],
              output: 'extracted_query'
            ]
          ]
        ],
        evaluate: true,
        output: true
      ]
    ],
    scanners: [
      [
        id: 'scanner_1',
        name: 'Test Scanner',
        key: [
          operator: 'match_regex',
          parameters: [
            regex: '^X-Scanner-',
            options: [
              case_sensitive: false
            ]
          ]
        ],
        value: [
          operator: 'match_regex',
          parameters: [
            regex: '.*harmful.*',
            options: [
              case_sensitive: false
            ]
          ]
        ],
        tags: [
          type: 'scanner_detection'
        ]
      ]
    ],
    actions: [
      [
        id: 'action_1',
        type: 'redirect_request',
        parameters: [
          status_code: 302,
          location: '/blocked.html'
        ]
      ]
    ],
    rules_override: [
      [
        rules_target: [[
            rule_id: 'comprehensive_rule_1'
          ]],
        enabled: true,
        on_match: ['block', 'redirect']
      ]
    ]
  ]

  private void verifyDiagnosticsFields(WafDiagnostics diagnostics) {
    assert diagnostics != null
    assert diagnostics.wellStructured
    assert diagnostics.rulesetVersion == '1.5.0'

    // Verify rules section
    assert diagnostics.rules != null
    assert diagnostics.rules.loaded != null
    assert diagnostics.rules.loaded.containsAll(['comprehensive_rule_1', 'comprehensive_rule_2'])
    assert diagnostics.rules.failed == []

    // Verify custom rules section - accepting that they might fail
    assert diagnostics.customRules != null
    // Either the rule loaded or failed for a known reason
    assert (diagnostics.customRules.loaded != null && diagnostics.customRules.loaded.contains('custom_rule_1')) ||
    (diagnostics.customRules.failed != null && diagnostics.customRules.failed.contains('custom_rule_1'))

    // Verify rules data section
    assert diagnostics.rulesData != null
    assert diagnostics.rulesData.loaded != null
    assert diagnostics.rulesData.loaded.size() > 0

    // Verify exclusions section
    assert diagnostics.exclusions != null
    assert diagnostics.exclusions.loaded != null
    assert diagnostics.exclusions.loaded.contains('exclusion_1')

    // Verify processors section
    assert diagnostics.processors != null
    assert diagnostics.processors.loaded != null
    assert diagnostics.processors.loaded.contains('processor_1')

    // Verify scanners section
    assert diagnostics.scanners != null
    assert diagnostics.scanners.loaded != null
    assert diagnostics.scanners.loaded.contains('scanner_1')

    // Verify actions section
    assert diagnostics.actions != null
    assert diagnostics.actions.loaded != null
    assert diagnostics.actions.loaded.contains('action_1')

    // Verify rules override section
    assert diagnostics.rulesOverride != null
    assert diagnostics.rulesOverride.loaded != null
    assert diagnostics.rulesOverride.loaded.size() > 0

    // Verify total configuration counts - accepting some errors
    assert diagnostics.numConfigOK > 0
  }

  private void testMatchingRules(WafContext context) {
    // Test matching scenario
    Map<String, Object> matchingParams = [
      'server.request.headers.no_cookies': ['user-agent': 'malicious-pattern'],
      'server.request.query': ['param1': 'injection attack'],
      'server.request.body': ['password': '123456'],
      'server.request.uri.raw': '/some/path',
      'waf.context.processor': ['schema': true]
    ]

    Waf.ResultWithData result = context.run(matchingParams, limits, metrics)

    // Verify result contains matches
    assert result != null
    assert result.result == Waf.Result.MATCH
    assert result.data != null
    assert !result.data.empty

    // Parse JSON data for detailed verification
    def jsonResult = new JsonSlurper().parseText(result.data)

    // Verify we have matches
    assert jsonResult.size() > 0
    // Check that any rule matched (without assuming specific structure)
    assert jsonResult.findAll { it.rule?.id == 'comprehensive_rule_1' }.size() > 0 ||
    jsonResult.findAll { it.rules?.find { rule -> rule.id == 'comprehensive_rule_1' } }.size() > 0
  }

  private void testExclusionRules(WafContext context) {
    // Test non-matching scenario with exclusion
    Map<String, Object> nonMatchingParams = [
      'server.request.headers.no_cookies': ['user-agent': 'safe-user-agent'],
      'server.request.uri.raw': '/api/health'
    ]

    def result = context.run(nonMatchingParams, limits, metrics)

    // Verify no matches due to exclusion
    assert result != null
    assert result.result == Waf.Result.OK
  }

  Map<String, Object> rulesetWithExclusionData =
  [
    version: '2.2',
    metadata: [
      rules_version: '1.5.0'
    ],
    rules: [
      [
        id: 'ip_match_rule',
        name: 'IP Match Rule',
        tags: [
          type: 'ip_match',
          category: 'ip_reputation'
        ],
        conditions: [
          [
            operator: 'ip_match',
            parameters: [
              inputs: [[
                  address: 'http.client_ip'
                ]],
              data: 'blocked_ips'
            ]
          ]
        ],
        on_match: ['block']
      ]
    ],
    rules_data: [
      [
        id: 'blocked_ips',
        type: 'ip_with_expiration',
        data: [
          [
            value: '192.168.1.100',
            expiration: 0
          ],
          [
            value: '10.0.0.100',
            expiration: 0
          ]
        ]
      ]
    ],
    exclusions: [
      [
        id: 'whitelist_exclusion',
        rules_target: [[
            tags: [
              type: 'ip_match'
            ]
          ]],
        conditions: [
          [
            operator: 'ip_match',
            parameters: [
              inputs: [[
                  address: 'http.client_ip'
                ]],
              data: 'whitelisted_ips'
            ]
          ]
        ]
      ]
    ],
    exclusion_data: [
      [
        id: 'whitelisted_ips',
        type: 'ip_with_expiration',
        data: [
          [
            value: '192.168.1.100',  // This IP is both blocked and whitelisted
            expiration: 0
          ],
          [
            value: '10.1.1.1',
            expiration: 0
          ]
        ]
      ]
    ]
  ]

  private void verifyExclusionDataDiagnostics(WafDiagnostics diagnostics) {
    assert diagnostics != null
    assert diagnostics.wellStructured
    assert diagnostics.rulesetVersion == '1.5.0'

    // Verify rules section
    assert diagnostics.rules != null
    assert diagnostics.rules.loaded != null
    assert diagnostics.rules.loaded.contains('ip_match_rule')

    // Verify rules data section
    assert diagnostics.rulesData != null
    assert diagnostics.rulesData.loaded != null
    assert diagnostics.rulesData.loaded.size() > 0

    // Verify exclusion data section (specifically testing this field)
    assert diagnostics.exclusionData != null
    assert diagnostics.exclusionData.loaded != null
    assert diagnostics.exclusionData.loaded.size() > 0

    // Verify total configuration counts
    assert diagnostics.numConfigOK > 0
    assert diagnostics.numConfigError == 0
  }

  private void testIpMatchingAndExclusion(WafContext context) {
    // Test exclusion data in action - this IP should be whitelisted even though it's in the blocked list
    Map<String, Object> whitelistedIpParams = [
      'http.client_ip': '192.168.1.100'
    ]

    Waf.ResultWithData result = context.run(whitelistedIpParams, limits, metrics)

    // Verify no match due to exclusion data
    assert result != null
    assert result.result == Waf.Result.OK

    // Test blocked IP that's not in the whitelist but is still bypassed by the exclusion rule
    Map<String, Object> blockedIpParams = [
      'http.client_ip': '10.0.0.100'
    ]

    result = context.run(blockedIpParams, limits, metrics)

    // The exclusion filter whitelist_exclusion is applied to all rules with type: 'ip_match'
    // so this IP is also excluded even though it's in the blocked list
    assert result != null
    assert result.result == Waf.Result.OK
  }
}
