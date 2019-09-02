package io.sqreen.powerwaf

import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException
import org.junit.Test

class BadRuleTests implements PowerwafTrait {

    @Test(expected = UnclassifiedPowerwafException)
    void 'json of the atom is bogus'() {
        ctx = Powerwaf.createContext('test', [test_atom: '{}}'])
    }

    @Test(expected = UnclassifiedPowerwafException)
    void 'json of the atom is not an object'() {
        ctx = Powerwaf.createContext('test', [test_atom: '[]'])
    }
}
