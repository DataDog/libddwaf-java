import groovy.json.JsonSlurper
import io.sqreen.powerwaf.Powerwaf
import io.sqreen.powerwaf.PowerwafContext
import org.junit.After
import org.junit.BeforeClass
import org.junit.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is
import static io.sqreen.powerwaf.Powerwaf.ActionWithData

class BasicTests {
    @BeforeClass
    static void beforeClass() {
        Powerwaf.initialize(true)
    }

    long timeoutInUs = 10000
    PowerwafContext ctx

    JsonSlurper slurper = new JsonSlurper()

    @After
    void after() {
        if (ctx) {
            ctx.close()
        }
    }

    @Test
    void 'we can check the version'() {
        assertThat Powerwaf.version, is(0)
    }

    @Test
    void 'test running basic rule'() {
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
                }
              ]
            }'''

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
