package io.sqreen.detailed_metrics

import io.sqreen.jni.JNITrait
import org.junit.Test

import static org.hamcrest.MatcherAssert.assertThat

class RoundtripTests implements JNITrait {
    @Test
    void 'single request'() {
        def coll = new RequestDataCollection()
        def req = new RequestData(
                route: 'my route',
                overtimeCallback: 'overtime cb'
        )
        coll.requests.add(req)
        def measurement = new RequestData.Measurement(
                callback: 'measurement cb',
                timing: -42.2
        )
        req.measurements.add(measurement)
        byte[] msgpackData = coll.serialize()

        def json = toJson(msgpackData)

        // language=JSON
        def exp = '''[{
          "route": "my route",
          "overtime_cb": "overtime cb",
          "measurements": [
              {
                "cb": "measurement cb",
                "value": 42.19,
                "conditions_passed": true
              }
          ]
        }]'''

        assertThat json, JsonMatcher.matchesJson(exp)
    }

    @Test
    void 'test slow calls'() {
        def coll = new RequestDataCollection()
        def req = new RequestData()
        coll.requests.add req
        def arg = [
                'foo',
                5,
                3.09375d,
                3.09375f,
                true,
                false,
                null,
                ['a'],
                [foo: 'bar'],
                RoundtripTests
        ]
        req.slowCalls.add(new RequestData.SlowCall(
                callback: 'my cb',
                timing: 42.4242,
                arguments: [arg] as Object[]
        ))

        byte[] msgpackData = coll.serialize()

        def json = toJson(msgpackData)

        // language=JSON
        def exp = '''[{
          "measurements": [],
          "slow_calls": [
            {
              "cb": "my cb",
              "duration": 42.44,
              "passed_conditions": false,
              "arguments": [
                [
                  "foo",
                  5,
                  3.09375,
                  3.09375,
                  true,
                  false,
                  null,
                  ["a"],
                  {"foo": "bar"},
                  "object of type java.lang.Class"
                ]
              ]
            }
          ]
        }]'''

        assertThat json, JsonMatcher.matchesJson(exp)
    }

    private void testSlowCallArgs(Object[] args, String expArgs) {
        def coll = new RequestDataCollection()
        def req = new RequestData()
        coll.requests.add req

        req.slowCalls.add(new RequestData.SlowCall(
                callback: 'my cb',
                timing: 42.4242,
                arguments: args
        ))

        byte[] msgpackData = coll.serialize()

        def json = toJson(msgpackData)

        def exp = """[{
          "measurements": [],
          "slow_calls": [
            {
              "cb": "my cb",
              "duration": 42.44,
              "passed_conditions": false,
              "arguments": ${expArgs}
            }
          ]
        }]"""

        assertThat json, JsonMatcher.matchesJson(exp)
    }

    @Test
    void 'slow call arguments with exception'() {
        def args = [
                [size: { -> 4 }] as Collection,
                new HashMap() {
                    @Override
                    Set<Map.Entry> entrySet() {
                        [
                                [
                                        getKey: { -> 'key'},
                                        getValue: { -> throw new RuntimeException('foobar') }
                                ] as Map.Entry
                        ]
                    }
                },
                'good arg'
        ] as Object[]

        // language=JSON
        def expArgs = '''[
          "<error during conversion>",
          "<error during conversion>",
          "good arg"
        ]'''

        testSlowCallArgs(args, expArgs)
    }

    @Test
    void 'slow call arguments reaching max depth'() {
        def args = [
                [[[[[[[[[['replaced']]]]]]]]]]
        ] as Object[]

        // language=JSON
        def expArgs = '''[
          [[[[[[[[[["<max depth exceeded>"]]]]]]]]]]
        ]'''

        testSlowCallArgs args, expArgs
    }


    @Test
    void 'slow call arguments with self references'() {
        def args = [
                ['foob'],
                [:]
        ] as Object[]
        args[0] << args[0]
        args[0] << args[0][0] // string; not self-reference
        args[1][args[1]] = 'foobar'
        args[1]['foo'] = args[1]

        // language=JSON
        def expArgs = '''[
          [
            "foob",
            "<self-reference to collection>",
            "foob"
          ],
          {
            "<self-reference to map>": "foobar",
            "foo": "<self-reference to map>"
          }
        ]'''

        testSlowCallArgs args, expArgs
    }

    private String toJson(byte[] msgpackData) {
        def installPrefix =
                System.getenv('LIBSQREEN_INSTALL_PREFIX')
                ?: 'libsqreen/Debug/out'
        def exe = "$installPrefix/usr/local/bin/perf2json"
        if (!new File(exe).exists()) {
            exe += ".exe"
        }
        def p = [exe].execute()
        p.outputStream << msgpackData
        p.outputStream.close()
        p.text
    }
}
