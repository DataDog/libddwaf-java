package io.sqreen.powerwaf

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.sqreen.jni.JNITrait

@CompileStatic
trait ReqBodyTrait extends JNITrait {

    static final Map REQ_BODY_ATOM = (Map) new JsonSlurper().parseText('''
        {
          "version": "1.0",
          "events": [
            {
              "id": "req_body_rule",
              "name": "Request body capturing",
              "conditions": [
                {
                  "operation": "match_regex",
                  "parameters": {
                    "inputs": ["server.request.body.raw"],
                    "regex": "my string"
                  }
                }
              ],
              "tags": {
                "type": "req_body_detection"
              },
              "action": "record"
            }
          ]
        }
        ''')

    int maxDepth = 5
    int maxElements = 20
    int maxStringSize = 30
    long timeoutInUs = 200000 // 200 ms
    long runBudget = 0 // unspecified

    Powerwaf.Limits getLimits() {
        new Powerwaf.Limits(
                maxDepth, maxElements, maxStringSize, timeoutInUs, runBudget)
    }

    Powerwaf.ActionWithData testWithData(Object data) {
        def rule = REQ_BODY_ATOM

        def params = [
                'server.request.body.raw': data
        ]

        def ctx = Powerwaf.createContext('test', rule)
        ctx.runRules(params, limits)
    }
}
