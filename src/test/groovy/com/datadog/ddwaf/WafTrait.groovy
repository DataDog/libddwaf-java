/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.AfterClass
import org.junit.Before

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

@CompileStatic
trait WafTrait extends JNITrait {

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
                    "inputs": ["server.request.headers.no_cookies:user-agent"],
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
                    ],
                    "regex": "^Arachni\\\\/v"
                  },
                  "operator": "match_regex"
                }
              ],
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
            },
            {
              "id": "extended_data",
              "type": "extended_data_collection",
              "parameters": {
                "headers_redaction": true,
                "max_collected_headers": 5
              }
            }
          ],
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
                    ],
                    "regex": "^Arachni\\\\/v"
                  },
                  "operator": "match_regex"
                }
              ],
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
                            ],
                            "regex": "^Dummy"
                        },
                        "operator": "match_regex"
                    }
                ],
                "on_match": ["stack_trace", "redirect2", "extended_data"]
            }
          ]
        }''')

  private WafBuilder origBuilder
  WafBuilder builder
  WafHandle handle
  WafContext context
  WafMetrics metrics
  WafDiagnostics wafDiagnostics

  JsonSlurper slurper = new JsonSlurper()

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
    origBuilder = this.builder
    metrics = new WafMetrics()
    maxDepth = 5
    maxElements = 20
    maxStringSize = 100
    timeoutInUs = 200000000 // 200 us
    runBudget = 0 // unspecified
  }

  @After
  @SuppressWarnings('ExplicitGarbageCollection')
  void after() {
    if (builder?.online) {
      builder.close()
    }
    // The test may have created a new builder and ignored the original
    if (origBuilder != builder && origBuilder?.online) {
      origBuilder.close()
    }
    if (handle?.online) {
      handle.close()
    }
    if (context?.online) {
      context.close()
    }

    // Check that all buffers were reset
    ByteBufferSerializer.ArenaPool.INSTANCE.arenas.each { arena ->
      arena.pwargsSegments.each { segment ->
        assertThat segment.buffer.position(), is(0)
      }
      arena.stringsSegments.each { segment ->
        assertThat segment.buffer.position(), is(0)
      }
    }

    // Force garbage collection to detect object leaks
    System.gc()
  }

  @AfterClass
  @SuppressWarnings('ExplicitGarbageCollection')
  static void afterClass() {
    System.gc()
  }

  @SuppressWarnings(value = ['UnnecessaryCast', 'UnsafeImplementationAsMap'])
  Waf.ResultWithData runRules(Object data) {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle?.close()
    context?.close()
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    context.run([
      'server.request.headers.no_cookies': [
        'user-agent': data
      ]
    ] as Map<String, Object>, limits, metrics)
  }
}
