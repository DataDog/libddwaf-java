package io.sqreen.powerwaf

import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.containsString

class InvalidInvocationTests implements PowerwafTrait {
    @Test
    void 'rule is run on closed context'() {
        ctx = Powerwaf.createContext('test', ARACHNI_ATOM)
        ctx.close()
        def exc = shouldFail(UnclassifiedPowerwafException) {
            ctx.runRules([:], limits)
        }
        assertThat exc.message, containsString('This context is already offline')
        ctx = null
    }
}
