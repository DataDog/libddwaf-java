package io.sqreen.powerwaf

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass

@CompileStatic
trait PowerwafTrait {

    static final String ARACHNI_ATOM = '''
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

    long timeoutInUs = 10000

    @BeforeClass
    static void beforeClass() {
        boolean simpleInit = System.getProperty("useReleaseBinaries") == null
        Powerwaf.initialize(simpleInit)
    }

    // do not deinitialize. Even when running the tests in a separate classloader,
    // Groovy holds caches with soft references that prevent the classloader from
    // being garbage collect and its native library from being unloaded in the finalizer
    // Therefore, the library would be reloaded and reinitialized and would stay
    // uninitialized for subsequent tests
//    @AfterClass
//    static void afterClass() {
//        Powerwaf.deinitialize()
//    }

    PowerwafContext ctx

    JsonSlurper slurper = new JsonSlurper()

    @After
    void after() {
        if (ctx) {
            ctx.close()
        }
    }
}
