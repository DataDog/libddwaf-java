/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

@CompileStatic
trait ReqBodyTrait extends WafTrait {

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

    Waf.ResultWithData testWithData(Object data) {
        def rule = REQ_BODY_ATOM

        def params = [
                'server.request.body.raw': data
        ]

        ctx = ctx ?: Waf.createHandle('test', rule)
        ctx.runRules(params, limits, metrics)
    }
}
