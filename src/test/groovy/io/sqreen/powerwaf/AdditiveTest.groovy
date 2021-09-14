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

        ctx = new PowerwafContext('test', new JsonSlurper().parseText(rule))
        additive = ctx.openAdditive()

        Powerwaf.ActionWithData awd = additive.run([arg1: 'string 1'], limits)
        LOGGER.debug('ActionWithData after 1st runAdditive: {}', awd)
        assertThat awd.action, is(Powerwaf.Action.OK)

        awd = additive.run([arg2: 'string 2'], limits)
        LOGGER.debug('ActionWithData after 2nd runAdditive: {}', awd)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
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
        ctx = new PowerwafContext('test', ARACHNI_ATOM)
        additive = ctx.openAdditive()
        additive.runAdditive([:], null)
    }

    @Test
    void 'should defer context destruction if the context is closed'() {
        ctx = new PowerwafContext('test', ARACHNI_ATOM)
        additive = ctx.openAdditive()
        assert ctx.refcount.get() == 2
        ctx.delReference()
        additive.runAdditive([:], limits)
        assert ctx.refcount.get() == 1
        additive.close()
        assert ctx.refcount.get() == 0

        /* prevent @After hooks from trying to close them */
        ctx = null
        additive = null
    }
}
