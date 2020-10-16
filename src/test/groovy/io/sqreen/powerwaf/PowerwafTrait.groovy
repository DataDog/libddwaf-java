package io.sqreen.powerwaf

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.sqreen.jni.JNITrait
import org.junit.After
import org.junit.BeforeClass

@CompileStatic
trait PowerwafTrait extends JNITrait {

    static final String ARACHNI_ATOM = '''
        {
          "manifest": {
            "#._server['HTTP_USER_AGENT']": {
              "inherit_from": "#._server['HTTP_USER_AGENT']",
              "run_on_value": true,
              "run_on_key": true
            }
          },
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

    int maxDepth = 5
    int maxElements = 20
    int maxStringSize = 100
    long timeoutInUs = 100000 // 100 ms
    long runBudget = 0; // unspecified

    Powerwaf.Limits getLimits() {
        new Powerwaf.Limits(
                maxDepth, maxElements, maxStringSize, timeoutInUs, runBudget)
    }

    PowerwafContext ctx

    JsonSlurper slurper = new JsonSlurper()

    @After
    void after() {
        if (ctx) {
            ctx.close()
        }
    }
}
