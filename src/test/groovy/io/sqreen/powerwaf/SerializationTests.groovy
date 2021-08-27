package io.sqreen.powerwaf

import org.junit.Test

import java.nio.CharBuffer

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class SerializationTests implements ReqBodyTrait {

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
    }

    @Test
    void 'Should NOT MONITOR if CharBuffer shifted and break malicious data'() {
        char[] storedBody = 'my string' as char[]
        CharBuffer cs = CharBuffer.wrap(storedBody, 0, storedBody.length)
        cs.position(4)  // shift position on 4 bytes (break signature)
        Powerwaf.ActionWithData awd = testWithData(cs)     // pass HeapCharBuffer
        assertThat awd.action, is(Powerwaf.Action.OK)
    }

    @Test
    void 'Should MONITOR with data passed as CharSequence'() {
        StringBuffer sb = new StringBuffer('my string')
        Powerwaf.ActionWithData awd = testWithData(sb)     // pass CharSequence
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }
}
