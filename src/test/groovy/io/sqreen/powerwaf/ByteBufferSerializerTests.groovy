package io.sqreen.powerwaf

import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Test

import java.nio.ByteBuffer
import java.nio.CharBuffer

import static groovy.util.GroovyAssert.shouldFail
import static org.junit.Assert.assertThat
import static org.hamcrest.Matchers.*

class ByteBufferSerializerTests implements PowerwafTrait {

    @Lazy
    ByteBufferSerializer serializer = new ByteBufferSerializer(limits)

    ByteBufferSerializer.ArenaLease lease

    @After
    void after() {
        lease?.close()
    }

    @Test
    void 'can serialize a string'() {
        lease = serializer.serialize([my_key: 'my string'])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <SSTRING> my string
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'serializes a big string when size is larger than 10'() {
        lease = serializer.serialize([small: '0123456789', big: '01234567890'])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          small: <SSTRING> 0123456789
          big: <STRING> 01234567890
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'can have string lengths larger than 65535'() {
        maxStringSize = Integer.MAX_VALUE
        def s = 'a' * (2 ** 16)
        lease = serializer.serialize([key: s])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p """
        <MAP>
          key: <STRING> $s
        """
        assertThat res, is(exp)
    }

    @Test
    void 'can serialize a CharBuffer'() {
        char[] storedBody = 'my string' as char[]
        CharBuffer cb = CharBuffer.wrap(storedBody, 0, storedBody.length)

        lease = serializer.serialize([my_key: cb])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <SSTRING> my string
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'can serialize a long'() {
        long l = -2305843009213693952
        lease = serializer.serialize([my_key: l])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <SIGNED> -2305843009213693952
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'serializes nulls as empty maps'() {
        lease = serializer.serialize([my_key: null])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <MAP>
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'can serialize a big int as a long'() {
        BigInteger bi = 18446744073709551623.toLong() // 2^64 + 7
        lease = serializer.serialize([my_key: bi])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <SIGNED> 7
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'can serialize a boolean as a string'() {
        lease = serializer.serialize([my_key: [true, false]])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <ARRAY>
            <SSTRING> true
            <SSTRING> false
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'can serialize lists'() {
        def arr = [1, 2, 3]
        lease = serializer.serialize([my_key: arr])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <ARRAY>
            <SIGNED> 1
            <SIGNED> 2
            <SIGNED> 3
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'can serialize empty lists'() {
        lease = serializer.serialize([my_key: []])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <ARRAY>
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'can serialize arrays'() {
        def arr = [1, 2, 3] as byte[]
        lease = serializer.serialize([my_key: arr])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <ARRAY>
            <SIGNED> 1
            <SIGNED> 2
            <SIGNED> 3
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'can serialize maps'() {
        def map = [(1): 'xx', 2: 'yy']
        lease = serializer.serialize([my_key: map])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <MAP>
            1: <SSTRING> xx
            2: <SSTRING> yy
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'can serialize iterables'() {
        def list = [1, 2]
        def iterable = [
            iterator: { -> list.iterator() }
        ] as Iterable
        lease = serializer.serialize([my_key: iterable])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <ARRAY>
            <SIGNED> 1
            <SIGNED> 2
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'unknown values are serialized as empty maps'() {
        lease = serializer.serialize([my_key: new Object()])
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <MAP>
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'iteration yields fewer elements the second time'() {
        def list = [1, 2]
        def first = true
        def iterable = [
                iterator: { ->
                    if (first) {
                        first = false
                        list.iterator()
                    } else {
                        [1].iterator()
                    }
                }
        ] as Iterable

        shouldFail(ConcurrentModificationException) {
            serializer.serialize([key: iterable])
        }
    }

    @Test
    void 'map reports size larger than what is gotten through iteration'() {
        def orig = [b: 2]
        Map newMap = new Map() {
            @Delegate
            Map delegate = orig

            @Override
            int size() {
                2
            }
        }
        shouldFail(ConcurrentModificationException) {
            serializer.serialize(newMap)
        }
    }

    @Test
    void 'collection reports size larger than what is gotten through iteration'() {
        def orig = ['x']
        Collection newColl = new Collection() {
            @Delegate
            Collection delegate = orig

            @Override
            int size() {
                2
            }
        }
        shouldFail(ConcurrentModificationException) {
            serializer.serialize([a: newColl])
        }
    }

    @Test
    void 'force creation of new string segment'() {
        maxStringSize = Integer.MAX_VALUE

        def size = ByteBufferSerializer.STRINGS_MIN_SEGMENTS_SIZE + 1
        def str = 'x' * size
        2.times {
            serializer.serialize([key1: str, key2: 42]).withCloseable { lease ->
                String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
                assertThat res, containsString(str)
            }
        }
    }

    @Test
    void 'force creation of new pwargs segment'() {
        maxElements = Integer.MAX_VALUE

        int size = ByteBufferSerializer.MAIN_MIN_SEGMENTS_SIZE / ByteBufferSerializer.SIZEOF_PWARGS
        def arr = ['x'] * size
        2.times {
            serializer.serialize([key1: arr, key2: 'x']).withCloseable { lease ->
                String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
                assertThat res.count('\n'), is(size + 3)
            }
        }
    }

    // Limits

    @Test
    void 'observes max elements limit'() {
        maxElements = 5
        def obj = [a: 1, b: 2, c: [3, 4], d: 5, e: 6]
        lease = serializer.serialize(obj)

        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        // maps and arrays count towards the limits.
        // d is included because when the map starts there are still 4 elements
        // remaining and the amount of entries needs is preallocated before
        // going through them
        def exp = p '''
        <MAP>
          a: <SIGNED> 1
          b: <SIGNED> 2
          c: <ARRAY>
            <SIGNED> 3
          d: <MAP>
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'observes maximum depth'() {
        maxDepth = 2
        def obj = [ // 1
                a: [ // 2
                        [ // 3: elements here are not serialized anymore
                                b: 'd']]]
        lease = serializer.serialize(obj)

        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          a: <ARRAY>
            <MAP>
              b: <MAP>
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'observes maximum string size'() {
        maxStringSize = 3
        // the size is number of UTF-16 code units
        def str = "\uFFFD" * 3 + 'x'

        def obj = ['12\uAAAA4': str]
        lease = serializer.serialize(obj)

        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          12\uAAAA: <SSTRING> \uFFFD\uFFFD\uFFFD
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'additive basic usage'() {
        lease = ByteBufferSerializer.blankLease
        ByteBuffer bb1 = lease.serializeMore(limits, [a: 'b'])
        ByteBuffer bb2 = lease.serializeMore(limits, [c: 'd'])

        String res = Powerwaf.pwArgsBufferToString(bb1)
        def exp = p '''
        <MAP>
          a: <SSTRING> b
        '''
        assertThat res, is(exp)

        res = Powerwaf.pwArgsBufferToString(bb2)
        exp = p '''
        <MAP>
          c: <SSTRING> d
        '''
        assertThat res, is(exp)
    }

    @Test
    void 'first pwargs buffer with nothing written'() {
        lease = ByteBufferSerializer.blankLease
        shouldFail(IllegalStateException) { lease.firstPWArgsByteBuffer }
    }

    @Test
    void 'serialize map with negative size'() {
        def exc = shouldFail(RuntimeException) {
            lease = serializer.serialize(new NegativeSizeMap<>(delegate: [key: 'value']))
        }
        assert exc.message == 'Negative map size'
    }

    @Test
    void 'serialize collection with negative size'() {
        def exc = shouldFail(RuntimeException) {
            lease = serializer.serialize([key: new NegativeSizeCollection<>(delegate: [1])])
        }
        assert exc.message == 'Negative collection size'
    }

    private static String p(String s) {
        s.stripIndent()[1..-1]
    }


    @CompileStatic
    static class NegativeSizeMap<K, V> implements Map<K, V> {
        @Delegate
        Map<K, V> delegate

        @Override
        int size() {
            - delegate.size();
        }
    }

    @CompileStatic
    static class NegativeSizeCollection<T> implements Collection<T> {
        @Delegate
        Collection<T> delegate

        @Override
        int size() {
            - delegate.size()
        }
    }
}
