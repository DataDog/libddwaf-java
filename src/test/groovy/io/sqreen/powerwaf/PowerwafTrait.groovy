package io.sqreen.powerwaf

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.sqreen.jni.JNITrait
import org.junit.After

@CompileStatic
trait PowerwafTrait extends JNITrait {

    static final Map ARACHNI_ATOM = (Map) new JsonSlurper().parseText('''
        {
          "version": "0.0",
          "events": [
            {
              "id": "arachni_rule",
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
                "type": "arachni_detection"
              },
              "action": "record"
            }
          ]
        }''')

    int maxDepth = 5
    int maxElements = 20
    int maxStringSize = 100
    long timeoutInUs = 200000 // 200 ms
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

    @SuppressWarnings('UnnecessaryCast')
    Powerwaf.ActionWithData runRules(Object data) {
        ctx.runRules([
                'server.request.headers.no_cookies': [
                        'user-agent': data
                ]
        ] as Map<String, Object>, limits)
    }
}
