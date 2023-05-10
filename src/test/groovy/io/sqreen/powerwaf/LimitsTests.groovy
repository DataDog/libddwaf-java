/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf

import groovy.json.JsonSlurper
import io.sqreen.powerwaf.exception.TimeoutPowerwafException
import org.junit.Ignore
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.oneOf

class LimitsTests implements PowerwafTrait {

    @Lazy
    PowerwafContext ctxWithArachniAtom =
            Powerwaf.createContext('test', ARACHNI_ATOM_V1_0)

    @Test
    void 'maxDepth is respected'() {
        ctx = ctxWithArachniAtom
        maxDepth = 3

        Powerwaf.ResultWithData awd = runRules(['Arachni'])
        assertThat awd.result, is(Powerwaf.Result.MATCH)

        awd = runRules([['Arachni']])
        assertThat awd.result, is(Powerwaf.Result.OK)
    }

    @Test
    void 'maxDepth is respected — array variant'() {
        ctx = ctxWithArachniAtom
        maxDepth = 3

        Powerwaf.ResultWithData awd = runRules(['Arachni'] as String[])
        assertThat awd.result, is(Powerwaf.Result.MATCH)

        awd = runRules([['Arachni'] as String[]] as Object[])
        assertThat awd.result, is(Powerwaf.Result.OK)
    }

    @Test
    void 'maxDepth is respected — map variant'() {
        ctx = ctxWithArachniAtom
        maxDepth = 3

        Powerwaf.ResultWithData awd = runRules([a: 'Arachni'])
        assertThat awd.result, is(Powerwaf.Result.MATCH)

        awd = runRules([a: [a: 'Arachni']])
        assertThat awd.result, is(Powerwaf.Result.OK)
    }

    @Test
    void 'maxDepth is respected — MapIterableWithSize variant'() {
        ctx = ctxWithArachniAtom
        maxDepth = 3

        Powerwaf.ResultWithData awd = runRules(asMiws(a: 'Arachni'))
        assertThat awd.result, is(Powerwaf.Result.MATCH)

        awd = runRules([a: asMiws(a: 'Arachni')])
        assertThat awd.result, is(Powerwaf.Result.OK)
    }

    @Test
    void 'maxElements is respected'() {
        ctx = ctxWithArachniAtom
        maxElements = 5

        Powerwaf.ResultWithData awd = runRules(['a', 'Arachni'])
        assertThat awd.result, is(Powerwaf.Result.MATCH)

        // the map and list count as elements
        awd = runRules(['a', 'b', 'Arachni'])
        assertThat awd.result, is(Powerwaf.Result.OK)
    }

    @Test
    void 'maxElements is respected — array variant'() {
        ctx = ctxWithArachniAtom
        maxElements = 5

        Powerwaf.ResultWithData awd = runRules(['a', 'Arachni'] as String[])
        assertThat awd.result, is(Powerwaf.Result.MATCH)

        // the map and list count as elements
        awd = runRules(['a', 'b', 'Arachni'] as String[])
        assertThat awd.result, is(Powerwaf.Result.OK)
    }

    @Test
    void 'maxElements is respected — map variant'() {
        ctx = ctxWithArachniAtom
        maxElements = 5

        Powerwaf.ResultWithData awd = runRules([a: 'a', b: 'Arachni'])
        assertThat awd.result, is(Powerwaf.Result.MATCH)

        // the map and list count as elements
        awd = runRules([a: 'a', b: 'b', c: 'Arachni'] as String[])
        assertThat awd.result, is(Powerwaf.Result.OK)
    }

    @Test
    void 'maxStringSize is observed'() {
        ctx = ctxWithArachniAtom
        maxStringSize = 100

        Powerwaf.ResultWithData awd = runRules(' ' * 93 + 'Arachni')
        assertThat awd.result, is(Powerwaf.Result.MATCH)

        awd = runRules(' ' * 94 + 'Arachni')
        assertThat awd.result, is(Powerwaf.Result.OK)
    }

    @Test
    void 'maxStringSize is observed — map key variant'() {
        ctx = ctxWithArachniAtom
        maxStringSize = 100

        Powerwaf.ResultWithData awd = runRules([(' ' * 93 + 'Arachni'): 'a'])
        // expected failure: running on keys is not possible now on libddwaf
        shouldFail(AssertionError) {
            assertThat awd.result, is(Powerwaf.Result.MATCH)
        }

        awd = runRules([(' ' * 94 + 'Arachni'): 'a'])
        assertThat awd.result, is(Powerwaf.Result.OK)
    }

    @Test
    void 'generalBudgetInUs is observed during PWARgs conversion'() {
        ctx = ctxWithArachniAtom
        timeoutInUs = 5

        shouldFail(TimeoutPowerwafException) {
            runRules([['Arachni']])
        }
    }

    @Test
    @Ignore
    void 'runBudgetInUs is observed'() {
        def atom = new JsonSlurper().parseText('''
          {
            "version": "1.0",
            "events": [
              {
                "id": "arachni_rule1",
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
                  "type": "arachni_detection1"
                },
                "action": "record"
              },
              {
                "id": "arachni_rule2",
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
                  "type": "arachni_detection2"
                },
                "action": "record"
              }
            ]
          }''')

        ctx = Powerwaf.createContext('test', atom)

        timeoutInUs = 10000000 // 10 sec
        runBudget = 10 // 10 microseconds
        maxStringSize = Integer.MAX_VALUE

        def res = runRules('Arachni' * 9000)
        assertThat res.result, is(oneOf(
                Powerwaf.Result.MATCH,
                Powerwaf.Result.OK)) // depending if it happened on first or 2nd rule

        def json = slurper.parseText(res.data)
        assertThat json.ret_code, hasItem(is(new TimeoutPowerwafException().code))
    }

    private MapIterableWithSize asMiws(Map m) {
        [
                size: { -> m.size() },
                iterator: { -> m.entrySet().iterator() }
        ] as MapIterableWithSize
    }
}
