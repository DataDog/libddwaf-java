package io.sqreen.powerwaf

import io.sqreen.powerwaf.exception.NoRulePowerwafException
import io.sqreen.powerwaf.exception.TimeoutPowerwafException
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.isOneOf

class InvalidInvocationTests implements PowerwafTrait {

    @Test(expected = NoRulePowerwafException)
    void 'rule does not exist'() {
        ctx = Powerwaf.createContext('test', [test_atom: ARACHNI_ATOM])
        ctx.runRule('bar', [:], limits)
    }

    @Test
    void 'a timeout occurs'() {
        def atom = '''
            {
              "rules":[
                {
                  "rule_id":"1",
                  "filters":[
                    {
                      "operator":"@rx",
                      "targets":[
                        "#._server['HTTP_USER_AGENT']"
                      ],
                      "value":"Arachni"
                    }
                  ]
                }
              ],
              "flows":[
                {
                  "name":"arachni_detection",
                  "steps":[
                    {
                      "id":"start",
                      "rule_ids":[
                        "1"
                      ],
                      "on_match":"exit_monitor"
                    }
                  ]
                },
                {
                  "name":"arachni_detection2",
                  "steps":[
                    {
                      "id":"start",
                      "rule_ids":[
                        "1"
                      ],
                      "on_match":"exit_monitor"
                    }
                  ]
                }
              ]
            }'''
        ctx = Powerwaf.createContext('test', [test_atom: atom])

        timeoutInUs = 700 // enough to pass timeout of parameter conversion
        maxStringSize = Integer.MAX_VALUE

        def res
        try {
            res = ctx.runRule('test_atom',
                    ["#._server['HTTP_USER_AGENT']": 'Arachni' * 9000],
                    limits)
        } catch (TimeoutPowerwafException tfe) {
            // this can happen in very slow environments
            return
        }
        assertThat res.action, isOneOf(
                Powerwaf.Action.MONITOR,
                Powerwaf.Action.OK) // depending if it happened on first or 2nd rule

        def json = slurper.parseText(res.data)
        assertThat json.ret_code, hasItem(is(-5))
    }

    @Test
    void 'rule is run on closed context'() {
        ctx = Powerwaf.createContext('test', [test_atom: ARACHNI_ATOM])
        ctx.close()
        def exc = shouldFail(UnclassifiedPowerwafException) {
            ctx.runRule('bar', [:], limits)
        }
        assertThat exc.message, containsString('This context is already offline')
        ctx = null
    }
}
