package io.sqreen.powerwaf

import groovy.json.JsonSlurper
import org.junit.Test

import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.isA

class SchemaTests implements PowerwafTrait {
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
                    "output": "server.request.body.schema"
                  }
                ]
              },
              "evaluate": false,
              "output": true
            }
          ],
          "output": true
        }''')

    @Test
    void 'extract schema'() {
        maxElements = 30
        timeoutInUs = 20000000
        runBudget = 20000000
        ctx = Powerwaf.createContext('test', EXTRACT_SCHEMA)

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
                        ]
                ]
        ]

        Powerwaf.ResultWithData awd = ctx.runRules(data, limits, metrics)
        assertThat awd.schemas, isA(Map)

        def schema = new JsonSlurper().parseText(decodeGzipBase64(awd.schemas.values().first()))
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
