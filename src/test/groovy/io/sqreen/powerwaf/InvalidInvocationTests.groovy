package io.sqreen.powerwaf

import io.sqreen.powerwaf.exception.NoRulePowerwafException
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.containsString

class InvalidInvocationTests implements PowerwafTrait {

    @Test(expected = NoRulePowerwafException)
    void 'rule does not exist'() {
        ctx = Powerwaf.createContext('test', [test_atom: ARACHNI_ATOM])
        ctx.runRule('bar', [:], limits)
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
