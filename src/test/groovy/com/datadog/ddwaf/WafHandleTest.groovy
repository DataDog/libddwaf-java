/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import groovy.json.JsonSlurper
import com.datadog.ddwaf.exception.AbstractWafException
import com.datadog.ddwaf.exception.TimeoutWafException
import com.datadog.ddwaf.exception.UnclassifiedWafException
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.is

class WafHandleTest implements ReactiveTrait {

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

        ctx = new WafHandle('test', null, new JsonSlurper().parseText(rule))
        wafContext = ctx.openContext()
        metrics = ctx.createMetrics()

        Waf.ResultWithData awd = wafContext.run([arg1: 'string 1'], limits, metrics)
        LOGGER.debug('ResultWithData after 1st runWafContext: {}', awd)
        assertThat awd.result, is(Waf.Result.OK)

        awd = wafContext.run([arg2: 'string 2'], limits, metrics)
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

        ctx = new WafHandle('test', null, new JsonSlurper().parseText(rule))
        wafContext = ctx.openContext()
        metrics = ctx.createMetrics()

        Waf.ResultWithData awd = wafContext.run([server_request_body: 'bodytest'], limits, metrics)
        LOGGER.debug('ResultWithData after 1st runWafContext: {}', awd)
        assertThat awd.result, is(Waf.Result.MATCH)

        awd = wafContext.runEphemeral([graphql_server_all_resolvers: 'graphqltest'], limits, metrics)
        LOGGER.debug('ResultWithData after 2st runWafContext: {}', awd)
        assertThat awd.result, is(Waf.Result.MATCH)
    }

    @Test
    void 'timeout when general budget is exhausted'() {
        final limits = new Waf.Limits(100, 100, 100, 0, Long.MAX_VALUE)
        ctx = new WafHandle('test', null, ARACHNI_ATOM_V2_1)
        wafContext = ctx.openContext()
        metrics = ctx.createMetrics()
        shouldFail(TimeoutWafException) {
            wafContext.run([arg1: 'string 1'], limits, metrics)
        }
    }

    @Test
    void 'throw an exception when both persistent and ephemeral are null in wafContext'() {
        ctx = new WafHandle('test', null, ARACHNI_ATOM_V2_1)
        wafContext = ctx.openContext()
        metrics = ctx.createMetrics()
        shouldFail(AbstractWafException) {
            wafContext.run(null, null, limits, metrics)
        }
    }

    @Test
    void 'throw an exception when both persistent and ephemeral are null'() {
        ctx = new WafHandle('test', null, ARACHNI_ATOM_V2_1)
        metrics = ctx.createMetrics()
        shouldFail(AbstractWafException) {
            ctx.runRules(null, limits, metrics)
        }
    }

    @Test
    void 'constructor throws if given a null context'() {
        shouldFail(NullPointerException) {
            new WafContext(null)
        }
    }

    @Test
    void 'should throw if double free'() {
        ctx = new WafHandle('test', null, ARACHNI_ATOM_V2_1)
        def wafContext = ctx.openContext()
        wafContext.close()
        final t = shouldFail(IllegalStateException) {
            wafContext.close()
        }
        assertThat t.message, is('This WafContext is no longer online')
    }

    @Test
    void 'should throw if run after close'() {
        ctx = new WafHandle('test', null, ARACHNI_ATOM_V2_1)
        def wafContext = ctx.openContext()
        wafContext.close()
        final t = shouldFail(UnclassifiedWafException) {
            wafContext.run([arg1: 'string 1'], limits, metrics)
        }
        assertThat t.message, containsString('This WafContext is no longer online')
    }

    @Test
    void 'should throw IllegalArgumentException if Limits is null while run'() {
        ctx = new WafHandle('test', null, ARACHNI_ATOM_V2_1)
        wafContext = ctx.openContext()
        shouldFail(IllegalArgumentException) {
            wafContext.run([:], null, metrics)
        }
    }

    @Test
    void 'context can be destroyed with live wafContext'() {
        new WafHandle('test', null, ARACHNI_ATOM_V2_1).withCloseable {
            wafContext = it.openContext()
        }
        Waf.ResultWithData rwd = wafContext.run([:], limits, metrics)
        assertThat rwd.result, is(Waf.Result.OK)
        wafContext.close()

        /* prevent @After hooks from trying to close it */
        wafContext = null
    }
}
