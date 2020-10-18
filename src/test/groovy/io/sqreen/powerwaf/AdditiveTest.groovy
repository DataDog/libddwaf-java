package io.sqreen.powerwaf

import org.junit.Test
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.nullValue

class AdditiveTest implements ReactiveTrait {

    /**
     * This is reference sample from PowerWAF converted to java
     * see: https://github.com/sqreen/PowerWAF#implementation-using-the-additive-api
     */
    @Test
    void 'Reference sample should pass'() {
        def rule = '''
            {
                "manifest": {
                    "arg1": {
                        "inherit_from": "arg1",
                        "run_on_value": true,
                        "run_on_key": false
                    },
                    "arg2": {
                        "inherit_from": "arg2",
                        "run_on_value": true,
                        "run_on_key": false
                    }
                },
                "rules": [
                    {
                        "rule_id": "1",
                        "filters": [
                            {
                                "operator": "@rx",
                                "targets": [
                                    "arg1"
                                ],
                                "value": ".*"
                            },
                            {
                                "operator": "@rx",
                                "targets": [
                                    "arg2"
                                ],
                                "value": ".*"
                            }
                        ]
                    }
                ],
                "flows": [
                    {
                        "name": "flow1",
                        "steps": [
                            {
                                "id": "start",
                                "rule_ids": [
                                    "1"
                                ],
                                "on_match": "exit_block"
                            }
                        ]
                    }
                ]
            }
        '''

        Powerwaf.addRule('test', rule)
        def additive = Additive.initAdditive('test')

        Powerwaf.ActionWithData awd = additive.runAdditive([arg1: 'string 1'], limits)
        assertThat awd.action, is(Powerwaf.Action.OK)

        awd = additive.runAdditive([arg2: 'string 2'], limits)
        assertThat awd.action, is(Powerwaf.Action.BLOCK)

        additive.clearAdditive()
        Powerwaf.clearRule('test')
    }

    @Test
    void 'Should trigger waf with native Additive Api only'() {
        def rule = ARACHNI_ATOM

        def params = [
                'server.request.uri.raw': '/',
                'server.request.headers.no_cookies': [
                        'accept': '*/*',
                        'user-agent': 'Arachni/v1',
                        'host': 'localhost:8080'
                ],
        ]

        Powerwaf.ActionWithData awd

        Powerwaf.addRule('test', rule)
        Additive additive = Additive.initAdditive('test')
        awd = additive.runAdditive(params, limits)
        additive.clearAdditive()
        Powerwaf.clearRule('test')

        assertThat awd.action, is(Powerwaf.Action.BLOCK)
    }

    @Test
    void 'Should trigger waf with Additive'() {
        def rule = ARACHNI_ATOM

        def params = [
                        'server.request.uri.raw': '/',
                        'server.request.headers.no_cookies': [
                                'accept': '*/*',
                                'user-agent': 'Arachni/v1',
                                'host': 'localhost:8080'
                        ],
                     ]

        Additive additive
        def ctx = new PowerwafContext('test', ['rule': rule])
        try {
            additive = ctx.openAdditive('rule')
            def awd = additive.run(params, limits)
            assertThat awd.action, is(Powerwaf.Action.BLOCK)
        } finally {
            if (additive != null) {
                additive.close()
            }
            ctx.close()
        }
    }

    @Test
    void 'Should return null if no rule found'() {
        Additive additive = Additive.initAdditive('nonexistent_rule')
        assertThat additive, nullValue()
    }

    @Test(expected = RuntimeException)
    void 'Should throw RuntimeException if double free'() {
        def rule = ARACHNI_ATOM

        Powerwaf.addRule('test', rule)
        Additive additive = Additive.initAdditive('test')
        additive.clearAdditive()
        additive.clearAdditive()
    }

    @Test(expected = IllegalArgumentException)
    void 'Should throw IllegalArgumentException if Limits is null while run'() {
        def rule = ARACHNI_ATOM

        Powerwaf.addRule('test', rule)
        Additive additive = Additive.initAdditive('test')
        additive.runAdditive([:], null)
    }
}
