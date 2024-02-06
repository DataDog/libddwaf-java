/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf

import groovy.json.JsonSlurper
import io.sqreen.powerwaf.exception.AbstractPowerwafException
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class AdditiveTest implements ReactiveTrait {

    private final static Logger LOGGER = LoggerFactory.getLogger(AdditiveTest)

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

        ctx = new PowerwafContext('test', null, new JsonSlurper().parseText(rule))
        additive = ctx.openAdditive()
        metrics = ctx.createMetrics()

        Powerwaf.ResultWithData awd = additive.run([arg1: 'string 1'], limits, metrics)
        LOGGER.debug('ResultWithData after 1st runAdditive: {}', awd)
        assertThat awd.result, is(Powerwaf.Result.OK)

        awd = additive.run([arg2: 'string 2'], limits, metrics)
        LOGGER.debug('ResultWithData after 2nd runAdditive: {}', awd)
        assertThat awd.result, is(Powerwaf.Result.MATCH)

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

        ctx = new PowerwafContext('test', null, new JsonSlurper().parseText(rule))
        additive = ctx.openAdditive()
        metrics = ctx.createMetrics()

        Powerwaf.ResultWithData awd = additive.run([server_request_body: 'bodytest'], limits, metrics)
        LOGGER.debug('ResultWithData after 1st runAdditive: {}', awd)
        assertThat awd.result, is(Powerwaf.Result.MATCH)

        awd = additive.runEphemeral([graphql_server_all_resolvers: 'graphqltest'], limits, metrics)
        LOGGER.debug('ResultWithData after 2st runAdditive: {}', awd)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }

    @Test
    void 'throw an exception when both persistent and ephemeral are null in additive'() {
        shouldFail(AbstractPowerwafException) {
            ctx = new PowerwafContext('test', null, ARACHNI_ATOM_V2_1)
            additive = ctx.openAdditive()
            metrics = ctx.createMetrics()

            additive.run(null, null, limits, metrics)
        }
    }

    @Test
    void 'throw an exception when both persistent and ephemeral are null'() {
        shouldFail(AbstractPowerwafException) {
            ctx = new PowerwafContext('test', null, ARACHNI_ATOM_V2_1)
            metrics = ctx.createMetrics()

            ctx.runRules(null, null, limits, metrics)
        }
    }

    @Test
    void 'constructor throws if given a null context'() {
        shouldFail(NullPointerException) {
            new Additive(null)
        }
    }

    @Test(expected = RuntimeException)
    void 'Should throw RuntimeException if double free'() {
        ctx = new PowerwafContext('test', ARACHNI_ATOM)
        additive = ctx.openAdditive()
        additive.close()
        try {
            additive.close()
        } finally {
            additive = null
        }
    }

    @Test(expected = IllegalArgumentException)
    void 'Should throw IllegalArgumentException if Limits is null while run'() {
        ctx = new PowerwafContext('test', null, ARACHNI_ATOM_V2_1)
        additive = ctx.openAdditive()
        additive.run([:], null, metrics)
    }

    @Test
    void 'context can be destroyed with live additive'() {
        new PowerwafContext('test', null, ARACHNI_ATOM_V2_1).withCloseable {
            additive = it.openAdditive()
        }
        Powerwaf.ResultWithData rwd = additive.run([:], limits, metrics)
        assertThat rwd.result, is(Powerwaf.Result.OK)
        additive.close()

        /* prevent @After hooks from trying to close it */
        additive = null
    }
}
