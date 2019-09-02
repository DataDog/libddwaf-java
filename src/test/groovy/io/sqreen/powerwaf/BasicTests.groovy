import io.sqreen.powerwaf.Powerwaf
import io.sqreen.powerwaf.PowerwafTrait
import org.junit.Test

import static io.sqreen.powerwaf.Powerwaf.ActionWithData
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class BasicTests implements PowerwafTrait {

    @Test
    void 'we can check the version'() {
        assertThat Powerwaf.version, is(0)
    }

    @Test
    void 'test running basic rule'() {
        def atom = ARACHNI_ATOM

        ctx = Powerwaf.createContext('test', [test_atom: atom])

        ActionWithData awd = ctx.runRule('test_atom', ["#._server['HTTP_USER_AGENT']": 'Arachni'], timeoutInUs)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)

        def json = slurper.parseText(awd.data)
        assert json.ret_code == [1]
        assert json.flow == ['arachni_detection']
        assert json.step == ['start']
        assert json.rule == ['1']
    }
}
