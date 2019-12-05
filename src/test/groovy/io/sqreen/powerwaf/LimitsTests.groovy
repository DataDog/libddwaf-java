package io.sqreen.powerwaf

import io.sqreen.powerwaf.exception.TimeoutPowerwafException
import org.junit.Before
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.isOneOf

class LimitsTests implements PowerwafTrait {

    @Before
    void before() {
        def atom = ARACHNI_ATOM
        ctx = Powerwaf.createContext('test', [test_atom: atom])
    }

    @Test
    void 'maxDepth is respected'() {
        maxDepth = 2

        Powerwaf.ActionWithData awd = ctx.runRule('test_atom',
                ["#._server['HTTP_USER_AGENT']": ['Arachni']], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)

        awd = ctx.runRule('test_atom',
                ["#._server['HTTP_USER_AGENT']": [['Arachni']]], limits)
        assertThat awd.action, is(Powerwaf.Action.OK)
    }

    @Test
    void 'maxDepth is respected — map variant'() {
        maxDepth = 2

        Powerwaf.ActionWithData awd = ctx.runRule('test_atom',
                ["#._server['HTTP_USER_AGENT']": [a: 'Arachni']], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)

        awd = ctx.runRule('test_atom',
                ["#._server['HTTP_USER_AGENT']": [a: [a:'Arachni']]], limits)
        assertThat awd.action, is(Powerwaf.Action.OK)
    }

    @Test
    void 'maxElements is respected'() {
        maxElements = 4

        Powerwaf.ActionWithData awd = ctx.runRule('test_atom',
                ["#._server['HTTP_USER_AGENT']": ['a', 'Arachni']], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)

        // the map and list count as elements
        awd = ctx.runRule('test_atom',
                ["#._server['HTTP_USER_AGENT']": ['a', 'b', 'Arachni']], limits)
        assertThat awd.action, is(Powerwaf.Action.OK)
    }

    @Test
    void 'maxStringSize is observed'() {
        maxStringSize = 100

        Powerwaf.ActionWithData awd = ctx.runRule('test_atom',
                ["#._server['HTTP_USER_AGENT']": ' ' * 93 + 'Arachni'], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)

        // the map and list count as elements
        awd = ctx.runRule('test_atom',
                ["#._server['HTTP_USER_AGENT']": ' ' * 94 + 'Arachni'], limits)
        assertThat awd.action, is(Powerwaf.Action.OK)
    }

    @Test
    void 'maxStringSize is observed — map key variant'() {
        maxStringSize = 100

        Powerwaf.ActionWithData awd = ctx.runRule('test_atom',
                ["#._server['HTTP_USER_AGENT']": [(' ' * 93 + 'Arachni'): 'a']], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)

        // the map and list count as elements
        awd = ctx.runRule('test_atom',
                ["#._server['HTTP_USER_AGENT']": [(' ' * 94 + 'Arachni'): 'a']], limits)
        assertThat awd.action, is(Powerwaf.Action.OK)
    }

    @Test
    void 'generalBudgetInUs is observed during PWARgs conversion'() {
        timeoutInUs = 5

        shouldFail(TimeoutPowerwafException) {
            ctx.runRule('test_atom',
                    ["#._server['HTTP_USER_AGENT']": [['Arachni']]], limits)
        }
    }

    @Test
    void 'runBudgetInUs is oberserved'() {
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

        timeoutInUs = 10000000 // 10 sec
        runBudget = 10 // 10 microseconds
        maxStringSize = Integer.MAX_VALUE

        def res = ctx.runRule('test_atom',
                ["#._server['HTTP_USER_AGENT']": 'Arachni' * 9000],
                limits)
        assertThat res.action, isOneOf(
                Powerwaf.Action.MONITOR,
                Powerwaf.Action.OK) // depending if it happened on first or 2nd rule

        def json = slurper.parseText(res.data)
        assertThat json.ret_code, hasItem(is(-5))
    }
}
