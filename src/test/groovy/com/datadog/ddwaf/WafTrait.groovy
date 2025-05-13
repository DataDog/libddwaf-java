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
                "on_match": ["stack_trace", "redirect2"]
            }
          ]
        }''')

  int maxDepth = 5
  int maxElements = 20
  int maxStringSize = 100
  long timeoutInUs = 200000 // 200 ms
  long runBudget = 0 // unspecified

  Waf.Limits getLimits() {
    new Waf.Limits(
      maxDepth, maxElements, maxStringSize, timeoutInUs, runBudget)
  }

  WafHandle ctx
  WafMetrics metrics

  JsonSlurper slurper = new JsonSlurper()

  @After
  void after() {
    ctx?.close()

    // Check that all buffers were reset
    ByteBufferSerializer.ArenaPool.INSTANCE.arenas.each { arena ->
      arena.pwargsSegments.each { segment ->
        assertThat segment.buffer.position(), is(0)
      }
      arena.stringsSegments.each { segment ->
        assertThat segment.buffer.position(), is(0)
      }
    }
  }

  @AfterClass
  @SuppressWarnings('ExplicitGarbageCollection')
  static void afterClass() {
    System.gc()
  }

  @SuppressWarnings(value = ['UnnecessaryCast', 'UnsafeImplementationAsMap'])
  Waf.ResultWithData runRules(Object data) {
    ctx.runRules([
      'server.request.headers.no_cookies': [
        'user-agent': data
      ]
    ] as Map<String, Object>, limits, metrics)
  }
}
