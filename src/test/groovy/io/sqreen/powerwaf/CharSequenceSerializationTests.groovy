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
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

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
    void 'Should MATCH with data passed as NativeStringAddressable'() {
        NativeStringAddressable nsa = new NSAImpl('my string')
        Powerwaf.ResultWithData awd = testWithData(nsa)
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
    void 'Should MATCH with data passed as direct CharBuffer with native order'() {
        char[] storedBody = 'my string' as char[]
        CharBuffer cs = ByteBuffer.allocateDirect(100).order(ByteOrder.nativeOrder()).asCharBuffer()
        cs.put(storedBody)
        cs.flip()
        Powerwaf.ResultWithData awd = testWithData(cs)
        assertThat awd.result, is(Powerwaf.Result.MATCH)
        assertThat cs.remaining(), is(storedBody.length)
    }

    @Test
    void 'Should MATCH with data passed as direct CharBuffer — variant with flipped order'() {
        ByteOrder flippedOrder = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ?
                ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN
        char[] storedBody = 'my string' as char[]
        CharBuffer cs = ByteBuffer.allocateDirect(100).order(flippedOrder).asCharBuffer()
        cs.order()
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

    @Test
    void 'Should not match if signature is past string limit — array CharBuffer variant'() {
        maxStringSize = 10
        char[] storedBody = '12my string' as char[]
        CharBuffer cs = CharBuffer.wrap(storedBody, 0, storedBody.length)
        Powerwaf.ResultWithData awd = testWithData(cs)
        assertThat awd.result, is(Powerwaf.Result.OK)
    }

    @Test
    void 'Should not match if signature is past string limit — StringCharBuffer variant'() {
        maxStringSize = 10
        CharBuffer cs = CharBuffer.wrap('12my string')
        Powerwaf.ResultWithData awd = testWithData(cs)
        assertThat awd.result, is(Powerwaf.Result.OK)
    }

    static class NSAImpl implements NativeStringAddressable {
        @Delegate
        CharSequence s
        ByteBuffer bb

        NSAImpl(String s) {
            this.s = s
            def bytes = s.getBytes(StandardCharsets.UTF_8)
            this.bb = ByteBuffer.allocateDirect(bytes.length + 1)
            this.bb.put(bytes)
            this.bb.put((byte)0)
        }

        @Override
        ByteBuffer getNativeStringBuffer() {
            bb
        }
    }
}
