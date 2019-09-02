package io.sqreen.powerwaf


import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.containsString

class EncodingTests implements PowerwafTrait {

    @Before
    void buildContext() {
        ctx = Powerwaf.createContext('test', [test_atom: ARACHNI_ATOM])
    }

    @Test
    void 'user input has an unpaired leading surrogate'() {
        Powerwaf.ActionWithData awd = ctx.runRule(
                'test_atom', ["#._server['HTTP_USER_AGENT']": 'Arachni\uD800'], timeoutInUs)

        def json = slurper.parseText(awd.data)
        assert json.filter.first().first().resolved_value == 'Arachni\uFFFD'
    }

    @Test
    void 'user input has unpaired leading surrogate'() {
        Powerwaf.ActionWithData awd = ctx.runRule(
                'test_atom', ["#._server['HTTP_USER_AGENT']": 'Arachni\uD800Ā'], timeoutInUs)

        def json = slurper.parseText(awd.data)
        assert json.filter.first().first().resolved_value == 'Arachni\uFFFDĀ'
    }

    @Test
    void 'user input has unpaired trailing surrogate'() {
        Powerwaf.ActionWithData awd = ctx.runRule(
                'test_atom', ["#._server['HTTP_USER_AGENT']": 'Arachni\uDC00x'], timeoutInUs)

        def json = slurper.parseText(awd.data)
        assert json.filter.first().first().resolved_value == 'Arachni\uFFFDx'
    }

    @Test
    void 'user input has two adjacent leading surrogates and does not invalidate the second'() {
        Powerwaf.ActionWithData awd = ctx.runRule(
                'test_atom', ["#._server['HTTP_USER_AGENT']": 'Arachni\uD800\uD801\uDC00'], timeoutInUs)

        assertThat awd.data, containsString('Arachni\uFFFD\uD801\uDC00')
    }

    @Test
    @Ignore('powerwaf is buggy')
    void 'user input has NUL character before and after matching part'() {
        Powerwaf.ActionWithData awd = ctx.runRule(
                'test_atom', ["#._server['HTTP_USER_AGENT']": '\u0000Arachni\u0000'], timeoutInUs)

        assertThat awd.data, containsString('\u0000Arachni\u0000')
    }
}
