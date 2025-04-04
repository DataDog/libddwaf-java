package com.datadog.ddwaf

import groovy.json.JsonSlurper
import org.junit.After
import org.junit.Before

class WafTestBase {

    static final Map ARACHNI_ATOM_V1_0 = (Map) new JsonSlurper().parseText('''
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
                    "inputs": ["server.request.headers.no_cookies:user-agent"] ,
                    "regex": "Arachni"
                  }
                }
              ] ,
              "tags": {
                "type": "arachni_detection"
              },
              "action": "record"
            }
          ]
        }''')

    static final Map ARACHNI_ATOM_V2_1 = (Map) new JsonSlurper().parseText('''
        {
          "version": "2.1",
          "metadata": {
            "rules_version": "1.2.6"
          },
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
                    "inputs": [
                      {
                        "address": "server.request.headers.no_cookies",
                        "key_path": [
                          "user-agent"
                        ]
                      }
                    ] ,
                    "regex": "^Arachni\\\\/v"
                  },
                  "operator": "match_regex"
                }
              ] ,
              "transformers": []
            }
          ]
        }''')

    static final Map ARACHNI_ATOM_BLOCK = (Map) new JsonSlurper().parseText('''
        {
          "version": "2.1",
          "metadata": {
            "rules_version": "1.2.6"
          },
          "actions": [
            {
              "id": "redirect1",
              "type": "redirect_request",
              "parameters": {
                "location": "https://example1.com/"
              }
            },
            {
              "id": "redirect2",
              "type": "redirect_request",
              "parameters": {
                "status_code": 301,
                "location": "https://example2.com/"
              }
            },
            {
              "id": "redirect3",
              "type": "redirect_request",
              "parameters": {
                "status_code": 400,
                "location": "https://example3.com/"
              }
            }
          ] ,
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
                    "inputs": [
                      {
                        "address": "server.request.headers.no_cookies",
                        "key_path": [
                          "user-agent"
                        ]
                      }
                    ] ,
                    "regex": "^Arachni\\\\/v"
                  },
                  "operator": "match_regex"
                }
              ] ,
              "on_match": ["block"]
            },
            {
                "id": "dummy_rule",
                "name": "Dummy",
                "tags": {
                    "type": "dummy"
                },
                "conditions": [
                    {
                        "parameters": {
                            "inputs": [
                                {
                                    "address": "server.request.headers.no_cookies",
                                    "key_path": [
                                        "user-agent"
                                    ]
                                }
                            ] ,
                            "regex": "^Dummy"
                        },
                        "operator": "match_regex"
                    }
                ] ,
                "on_match": ["stack_trace", "redirect2"]
            }
          ]
        }''')

    WafBuilder builder
    WafMetrics wafMetrics
    RuleSetInfo[] ruleSetInfo = new RuleSetInfo[1]
    Waf.Limits getLimits() {
        new Waf.Limits(
                maxDepth, maxElements, maxStringSize, timeoutInUs, runBudget)
    }

    int maxDepth
    int maxElements
    int maxStringSize
    long timeoutInUs
    long runBudget

    @Before
    void setup() {
        System.setProperty('ddwaf.logLevel', 'DEBUG')
        Waf.initialize(System.getProperty('useReleaseBinaries') == null)
        System.setProperty('DD_APPSEC_WAF_TIMEOUT', '500000' /* 500 ms */)
        builder = new WafBuilder() // initial config will always be default
        wafMetrics = new WafMetrics()
        maxDepth = 5
        maxElements = 20
        maxStringSize = 100
        timeoutInUs = 200000000 // 200 us
        runBudget = 0 // unspecified
    }

    @After
    @SuppressWarnings('ExplicitGarbageCollection')
    void afterClass() {
        if (builder?.online) {
            builder.destroy()
        }
        System.gc()
    }

    @SuppressWarnings(value = ['UnnecessaryCast', 'UnsafeImplementationAsMap'])
    Waf.ResultWithData runRules(Object data) {
        builder.addOrUpdateConfig('enya', ARACHNI_ATOM_V1_0, ruleSetInfo)
        Waf.runContext([
                'server.request.headers.no_cookies': [
                        'user-agent': data
                ]
        ], limits, wafMetrics, builder)
    }
}
