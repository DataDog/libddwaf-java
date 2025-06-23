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

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class WafHandleTest implements WafTrait {

  private final static Logger LOGGER = LoggerFactory.getLogger(WafHandleTest)

  @Test
  void 'Reference sample should pass'() {
    def rule = '''
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
                      "inputs": ["arg1"],
                      "regex": ".*"
                    }
                  },
                  {
                    "operation": "match_regex",
                    "parameters": {
                      "inputs": ["arg2"],
                      "regex": ".*"
                    }
                  }
                ],
                "tags": {
                  "type": "flow1"
                },
                "action": "record"
              }
            ]
          }
        '''
    builder.addOrUpdateConfig('test', new JsonSlurper().parseText(rule) as Map<String, Object>)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    Waf.ResultWithData awd = context.run([arg1: 'string 1'], limits, metrics)
    LOGGER.debug('ResultWithData after 1st runWafContext: {}', awd)
    assertThat awd.result, is(Waf.Result.OK)

    awd = context.run([arg2: 'string 2'], limits, metrics)
    LOGGER.debug('ResultWithData after 2nd runWafContext: {}', awd)
    assertThat awd.result, is(Waf.Result.MATCH)

    assert metrics.totalRunTimeNs > 0
    assert metrics.totalDdwafRunTimeNs > 0
    assert metrics.totalRunTimeNs >= metrics.totalDdwafRunTimeNs
  }

  @Test
  void 'Reference sample for rules 2_2'() {
    def rule = '''
          {
            "version": "2.2",
            "metadata": {
              "rules_version": "1.10.0"
            },
            "rules": [
              {
                "id": "rule1",
                "name": "rule1",
                "tags": {
                  "type": "flow1",
                  "category": "category1"
                },
                "conditions": [
                  {
                    "parameters": {
                      "inputs": [
                        {
                          "address": "server_request_body"
                        }
                      ],
                      "list": [
                        "bodytest"
                      ]
                    },
                    "operator": "phrase_match"
                  }
                ]
              },
              {
                "id": "rule2",
                "name": "rule2",
                "tags": {
                  "type": "flow2",
                  "category": "category2"
                },
                "conditions": [
                  {
                    "parameters": {
                      "inputs": [
                        {
                          "address": "graphql_server_all_resolvers"
                        }
                      ],
                      "list": [
                        "graphqltest"
                      ]
                    },
                    "operator": "phrase_match"
                  }
                ]
              }
            ],
          }'''
    builder.addOrUpdateConfig('test', new JsonSlurper().parseText(rule) as Map<String, Object>)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    Waf.ResultWithData awd = context.run([server_request_body: 'bodytest'], limits, metrics)
    LOGGER.debug('ResultWithData after 1st runWafContext: {}', awd)
    assertThat awd.result, is(Waf.Result.MATCH)

    awd = context.runEphemeral([graphql_server_all_resolvers: 'graphqltest'], limits, metrics)
    LOGGER.debug('ResultWithData after 2st runWafContext: {}', awd)
    assertThat awd.result, is(Waf.Result.MATCH)
  }

  @Test
  void 'waf handle is online after creation'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()

    assert handle.online
  }

  @Test
  void 'waf handle is offline after closing'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()
    handle.close()

    assert !handle.online
  }

  @Test
  void 'waf handle can be closed multiple times safely'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()

    // First close
    handle.close()
    assert !handle.online

    // Second close should not throw exception
    handle.close()
    assert !handle.online
  }

  @Test
  void 'waf handle has known addresses'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()

    String[] addresses = handle.knownAddresses
    assert addresses != null
    assert addresses.length > 0
  }

  @Test
  void 'waf handle has known actions'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_BLOCK)
    handle = builder.buildWafHandleInstance()

    String[] actions = handle.knownActions
    assert actions != null
    assert actions.length > 0
  }

  @Test
  void 'knownAddresses throws exception after handle is closed'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()
    handle.close()

    def exception = shouldFail(IllegalStateException) {
      handle.knownAddresses
    }
    assert exception.message == 'This WafHandle is no longer online'
  }

  @Test
  void 'getKnownActions throws exception after handle is closed'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_BLOCK)
    handle = builder.buildWafHandleInstance()
    handle.close()

    def exception = shouldFail(IllegalStateException) {
      handle.knownActions
    }
    assert exception.message == 'This WafHandle is no longer online'
  }

  @Test
  void 'different waf handle instances for same ruleset have same addresses'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)

    // Create two separate instances
    def handle1 = builder.buildWafHandleInstance()
    def handle2 = builder.buildWafHandleInstance()

    try {
      String[] addresses1 = handle1.knownAddresses
      String[] addresses2 = handle2.knownAddresses

      assert addresses1 != null
      assert addresses2 != null
      assert addresses1.length > 0
      assert addresses1.length == addresses2.length
    } finally {
      handle1.close()
      handle2.close()
    }
  }

  @Test
  void 'waf handle thread safety for read operations'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()

    // Simulate multiple threads accessing read methods
    def threads = []
    def exceptions = []

    10.times { i ->
      threads << Thread.start {
        try {
          handle.knownAddresses
        } catch (IllegalStateException e) {
          synchronized(exceptions) {
            exceptions << e
          }
        }
      }
    }

    // Wait for all threads to complete
    threads.each { it.join() }

    // No exceptions should have been thrown
    assert exceptions.size() == 0
  }

  @Test
  void 'waf handle thread safety for close operation'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()

    // Simulate multiple threads trying to close the handle
    def threads = []

    5.times { i ->
      threads << Thread.start {
        handle.close()
      }
    }

    // Wait for all threads to complete
    threads.each { it.join() }

    // Handle should be closed
    assert !handle.online
  }

  @Test
  void 'updating ruleset configuration works correctly'() {
    // First add a basic ruleset
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)

    // Then update with a different ruleset
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_BLOCK)

    handle = builder.buildWafHandleInstance()

    assert handle != null
    assert handle.online

    // Should have actions from the BLOCK ruleset
    String[] actions = handle.knownActions
    assert actions != null
    assert actions.length > 0
  }
}

