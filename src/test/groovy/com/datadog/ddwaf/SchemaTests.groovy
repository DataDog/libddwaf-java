package com.datadog.ddwaf

import groovy.json.JsonSlurper
import org.junit.Test

import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.isA

class SchemaTests implements WafTrait {
    static final Map EXTRACT_SCHEMA = (Map) new JsonSlurper().parseText('''
        {
          "version": "2.2",
          "metadata": {
            "rules_version": "1.8.0"
          },
          "rules": [
            {
              "id": "rule1",
              "name": "rule1",
              "tags": {
                "type": "flow1",
                "category": "category1"
              },
              "conditions": [
                {
                  "parameters": {
                    "inputs": [
                      {
                        "address": "server.request.body"
                      }
                    ],
                    "regex": "(?:runtime|processbuilder)",
                    "options": {
                      "case_sensitive": true,
                      "min_length": 5
                    }
                  },
                  "operator": "match_regex"
                }
              ]
            }
          ],
          "processors": [
            {
              "id": "preprocessor-001",
              "generator": "extract_schema",
              "conditions": [
                {
                  "operator": "equals",
                  "parameters": {
                    "inputs": [
                      {
                        "address": "waf.context.settings",
                        "key_path": [
                          "extract-schema"
                        ]
                      }
                    ],
                    "type": "boolean",
                    "value": true
                  }
                }
              ],
              "parameters": {
                "mappings": [
                  {
                    "inputs": [
                      {
                        "address": "server.request.body"
                      }
                    ],
                    "output": "_dd.appsec.s.req.body"
                  }
                ],
                "scanners": [
                  {
                    "tags": {
                      "category": "pii"
                    }
                  }
                ],
              },
              "evaluate": false,
              "output": true
            }
          ],
          "scanners": [
            {
              "id": "ac6d683cbac77f6e399a14990793dd8fd0fca333",
              "name": "US Vehicle Identification Number Scanner",
              "key": {
                "operator": "match_regex",
                "parameters": {
                  "regex": "vehicle[_\\\\s-]*identification[_\\\\s-]*number|vin",
                  "options": {
                    "case_sensitive": false,
                    "min_length": 3
                  }
                }
              },
              "value": {
                "operator": "match_regex",
                "parameters": {
                  "regex": "\\\\b[A-HJ-NPR-Z0-9]{17}\\\\b",
                  "options": {
                    "case_sensitive": false,
                    "min_length": 17
                  }
                }
              },
              "tags": {
                "type": "vin",
                "category": "pii"
              }
            }
          ],
          "output": true
        }''')

    @Test
    void 'extract schema'() {
        maxElements = 30
        timeoutInUs = 20000000
        runBudget = 20000000
        ctx = Waf.createHandle('test', EXTRACT_SCHEMA)

        def data = [
                'waf.context.settings': [
                        'extract-schema': true
                ],
                'server.request.body': [
                        a: 'foo',
                        b: 800,
                        c: 800.5d,
                        d: null,
                        e: true,
                        f: 800.4f,
                        g: 800.3,
                        h: [
                                4, 5, '6', [4, 5], [a: 'b'], 'foo', 'bar'
                        ],
                        vehicle_identification_number: 'WWW5R56GNG0000000'
                ]
        ]

        Waf.ResultWithData awd = ctx.runRules(data, limits, metrics)
        assertThat awd.derivatives, isA(Map)

        def schema = new JsonSlurper().parseText(decodeGzipBase64(awd.derivatives['_dd.appsec.s.req.body']))
        assert deepSortLists(schema) == [
                [
                 'a':[8],
                 'b':[4],
                 'c':[16],
                 'd':[1],
                 'e':[2],
                 'f':[16],
                 'g':[16],
                 'h':[['len':7], [[4], [8], [['a':[8]]], [[[4]], ['len':2]]]],
                 'vehicle_identification_number':[8, ['category':'pii', 'type':'vin']]
                ]
        ]
    }

    private static String decodeGzipBase64(String encodedData) {
        byte[] compressedData = Base64.decoder.decode(encodedData)
        ByteArrayInputStream bis = new ByteArrayInputStream(compressedData)
        GZIPInputStream gis = new GZIPInputStream(bis)
        ByteArrayOutputStream bos = new ByteArrayOutputStream()
        byte[] buffer = new byte[1024]
        int len
        while ((len = gis.read(buffer)) != -1) {
            bos.write(buffer, 0, len)
        }
        new String(bos.toByteArray(), StandardCharsets.UTF_8)
    }

    private Object deepSortLists(Object obj) {
        if (obj instanceof List) {
            obj.collect { deepSortLists(it) }.sort()
        } else if (obj instanceof Map) {
            // sort map keys too for good measure
            obj.collectEntries { key, value -> [key, deepSortLists(value)] }
                    .sort { a, b -> a.key <=> b.key }
        } else {
            obj
        }
    }
}
