/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf

import org.junit.Test

import java.nio.ByteBuffer
import java.nio.CharBuffer

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class CharSequenceSerializationTests implements ReqBodyTrait {

    @Test
    void 'Should MATCH with data passed as String'() {
        String str = 'my string'
        Powerwaf.ResultWithData awd = testWithData(str)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }

    @Test
    void 'Should MATCH with data passed as CharBuffer'() {
        char[] storedBody = 'my string' as char[]
        CharBuffer cs = CharBuffer.wrap(storedBody, 0, storedBody.length)
        Powerwaf.ResultWithData awd = testWithData(cs)     // pass HeapCharBuffer
        assertThat awd.result, is(Powerwaf.Result.MATCH)
        assertThat cs.remaining(), is(storedBody.length)
    }

    @Test
    void 'Should MATCH with data passed as direct CharBuffer'() {
        char[] storedBody = 'my string' as char[]
        CharBuffer cs = ByteBuffer.allocateDirect(100).asCharBuffer()
        cs.put(storedBody)
        cs.flip()
        Powerwaf.ResultWithData awd = testWithData(cs)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
        assertThat cs.remaining(), is(storedBody.length)
    }

    @Test
    void 'Should NOT MATCH if CharBuffer shifted and break malicious data'() {
        char[] storedBody = 'my string' as char[]
        CharBuffer cs = CharBuffer.wrap(storedBody, 0, storedBody.length)
        cs.position(4)  // shift position on 4 bytes (break signature)
        Powerwaf.ResultWithData awd = testWithData(cs)     // pass HeapCharBuffer
        assertThat awd.result, is(Powerwaf.Result.OK)
        assertThat cs.remaining(), is(storedBody.length - 4)
    }

    @Test
    void 'Should MATCH with data passed as CharSequence'() {
        StringBuffer sb = new StringBuffer('my string')
        Powerwaf.ResultWithData awd = testWithData(sb)     // pass CharSequence
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }

    @Test
    void 'Should MATCH with data passed as nondirect no array char buffer'() {
        CharBuffer buf = CharBuffer.wrap('my string')
        Powerwaf.ResultWithData awd = testWithData(buf)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
    }
}
