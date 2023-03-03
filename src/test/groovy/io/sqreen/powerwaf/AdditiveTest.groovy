/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf

import groovy.json.JsonSlurper
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.containsString
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
    void 'variant with MapIterableWithSize'() {
        ctx = new PowerwafContext('test', null, ARACHNI_ATOM_V2_1)
        additive = ctx.openAdditive()
        Map map = ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']]
        def miws = [
                size: { -> map.size() },
                iterator: { -> map.entrySet().iterator() }
        ] as MapIterableWithSize

        Powerwaf.ResultWithData awd = additive.run(miws, limits, metrics)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }

    @Test
    void 'constructor throws if given a null context'() {
        shouldFail(NullPointerException) {
            new Additive(null)
        }
    }

    @Test
    void 'Should throw RuntimeException if double free'() {
        ctx = new PowerwafContext('test', null, ARACHNI_ATOM_V2_1)
        additive = ctx.openAdditive()
        additive.close()
        try {
            def exc = shouldFail(IllegalStateException) {
                additive.close()
            }
            assertThat exc.message, containsString('is no longer online')
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
