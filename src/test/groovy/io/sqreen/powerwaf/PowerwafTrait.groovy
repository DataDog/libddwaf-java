/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.sqreen.jni.JNITrait
import org.junit.After
import org.junit.AfterClass

@CompileStatic
trait PowerwafTrait extends JNITrait {

    static final Map ARACHNI_ATOM_V1_0 = (Map) new JsonSlurper().parseText('''
        {
          "version": "1.0",
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

    static final Map ARACHNI_ATOM_V2_1 = (Map) new JsonSlurper().parseText('''
        {
          "version": "2.1",
          "rules": [
            {
              "id": "arachni_rule",
              "name": "Arachni",
              "tags": {
                "type": "security_scanner",
                "category": "attack_attempt"
              },
              "conditions": [
                {
                  "parameters": {
                    "inputs": [
                      {
                        "address": "server.request.headers.no_cookies",
                        "key_path": [
                          "user-agent"
                        ]
                      }
                    ],
                    "regex": "^Arachni\\\\/v"
                  },
                  "operator": "match_regex"
                }
              ],
              "transformers": []
            }
          ]
        }''')

    int maxDepth = 5
    int maxElements = 20
    int maxStringSize = 100
    long timeoutInUs = 200000 // 200 ms
    long runBudget = 0 // unspecified

    Powerwaf.Limits getLimits() {
        new Powerwaf.Limits(
                maxDepth, maxElements, maxStringSize, timeoutInUs, runBudget)
    }

    PowerwafContext ctx

    JsonSlurper slurper = new JsonSlurper()

    @After
    void after() {
        if (ctx) {
            ctx.delReference()
        }
    }

    @AfterClass
    @SuppressWarnings('ExplicitGarbageCollection')
    static void afterClass() {
        System.gc()
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
