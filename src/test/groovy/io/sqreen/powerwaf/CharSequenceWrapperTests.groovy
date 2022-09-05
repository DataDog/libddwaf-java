package io.sqreen.powerwaf

import io.sqreen.jni.JNITrait
import org.junit.Test

import java.nio.CharBuffer

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.*

class CharSequenceWrapperTests implements JNITrait {
    @Test
    void 'wraps a string in to a char buffer'() {
        CharBuffer buff = CharSequenceWrapper.wrap('my string', null)
        assertThat buff.getClass().name, is('java.nio.StringCharBuffer')
        assertThat buff.direct, is(false)
        assertThat buff.position(), is(0)
        assertThat buff.limit(), is('my string'.length())
        assertThat new String(buff.chars), is('my string')
    }

    @Test
    void 'can reuse a char buffer'() {
        CharBuffer buffOrig = CharSequenceWrapper.wrap('original', null)
        buffOrig.position(2)
        buffOrig.limit(3)
        CharBuffer buff = CharSequenceWrapper.wrap('my string', buffOrig)
        assertThat buff.direct, is(false)
        assertThat buff.position(), is(0)
        assertThat buff.limit(), is('my string'.length())
        assertThat new String(buff.chars), is('my string')
        assertThat buff, sameInstance(buffOrig)
    }
}
