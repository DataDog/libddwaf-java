package com.datadog.ddwaf

import groovy.json.JsonSlurper
import org.junit.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.contains
import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.matchesPattern

class FingerprintTests implements WafTrait {

  @Test
  void 'test fingerprints'() {
    final userAgent = 'Arachni/v1.5.1'
    final ruleSet = (Map) new JsonSlurper().parseText('''
{
  "version": "2.2",
  "metadata": {
    "rules_version": "1.8.0"
  },
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
                "key_path": [ "user-agent" ]
              }
            ],
            "regex": "^Arachni\\\\/v"
          },
          "operator": "match_regex"
        }
      ],
      "transformers": [],
      "on_match": ["block"]
    }
  ],
  "processors": [
    {
      "id": "processor-001",
      "generator": "http_endpoint_fingerprint",
      "conditions": [
        {
          "operator": "equals",
          "parameters": {
            "inputs": [
              {
                "address": "waf.context.processor",
                "key_path": [ "fingerprint" ]
              }
            ],
            "value": true,
            "type": "boolean"
          }
        }
      ],
      "parameters": {
        "mappings": [
          {
            "method": [ { "address": "server.request.method" } ],
            "uri_raw": [ { "address": "server.request.uri.raw" } ],
            "body": [ { "address": "server.request.body" } ],
            "query": [ { "address": "server.request.query" } ],
            "output": "_dd.appsec.fp.http.endpoint"
          }
        ]
      },
      "evaluate": true,
      "output": true
    }
  ]
}
''')

    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    Waf.ResultWithData res = context.run(
      [
        'waf.context.processor'            : ['fingerprint': true],
        'server.request.method'            : 'GET',
        'server.request.uri.raw'           : 'http://localhost:8080/test',
        'server.request.body'              : [:],
        'server.request.query'             : [name: ['test']],
        'server.request.headers.no_cookies': ['user-agent': [userAgent]]
      ],
      limits,
      metrics
      )
    assertThat res.result, is(Waf.Result.MATCH)
    assertThat res.derivatives.keySet(), contains('_dd.appsec.fp.http.endpoint')
    assertThat res.derivatives['_dd.appsec.fp.http.endpoint'], matchesPattern('http-get-.*')
  }
}

