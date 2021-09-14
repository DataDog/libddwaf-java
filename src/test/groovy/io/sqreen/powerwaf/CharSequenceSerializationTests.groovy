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
    void 'Should MONITOR with data passed as String'() {
        String str = 'my string'
        Powerwaf.ActionWithData awd = testWithData(str)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }

    @Test
    void 'Should MONITOR with data passed as CharBuffer'() {
        char[] storedBody = 'my string' as char[]
        CharBuffer cs = CharBuffer.wrap(storedBody, 0, storedBody.length)
        Powerwaf.ActionWithData awd = testWithData(cs)     // pass HeapCharBuffer
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
        assertThat cs.remaining(), is(storedBody.length)
    }

    @Test
    void 'Should MONITOR with data passed as direct CharBuffer'() {
        char[] storedBody = 'my string' as char[]
        CharBuffer cs = ByteBuffer.allocateDirect(100).asCharBuffer()
        cs.put(storedBody)
        cs.flip()
        Powerwaf.ActionWithData awd = testWithData(cs)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
        assertThat cs.remaining(), is(storedBody.length)
    }

    @Test
    void 'Should NOT MONITOR if CharBuffer shifted and break malicious data'() {
        char[] storedBody = 'my string' as char[]
        CharBuffer cs = CharBuffer.wrap(storedBody, 0, storedBody.length)
        cs.position(4)  // shift position on 4 bytes (break signature)
        Powerwaf.ActionWithData awd = testWithData(cs)     // pass HeapCharBuffer
        assertThat awd.action, is(Powerwaf.Action.OK)
        assertThat cs.remaining(), is(storedBody.length - 4)
    }

    @Test
    void 'Should MONITOR with data passed as CharSequence'() {
        StringBuffer sb = new StringBuffer('my string')
        Powerwaf.ActionWithData awd = testWithData(sb)     // pass CharSequence
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }

    @Test
    void 'Should MONITOR with data passed as nondirect no array char buffer'() {
        CharBuffer buf = CharBuffer.wrap('my string')
        Powerwaf.ActionWithData awd = testWithData(buf)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }
}
