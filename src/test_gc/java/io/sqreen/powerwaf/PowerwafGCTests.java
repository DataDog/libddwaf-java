package io.sqreen.powerwaf;

import io.sqreen.powerwaf.test.ChildFirstURLClassLoader;
import org.hamcrest.Matcher;
import org.junit.Test;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URL;

import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

public class PowerwafGCTests {
    ReferenceQueue refQueue;
    WeakReference weakRef;

    private void testBody() throws Exception {
        ClassLoader parentCl = PowerwafGCTests.class.getClassLoader();

        String urlSrc = parentCl.getResource("io/sqreen/powerwaf/Powerwaf.class").getFile();
        int endOfDirSrc = urlSrc.indexOf("io/sqreen/");
        String srcClassesDir = urlSrc.substring(0, endOfDirSrc);

        ChildFirstURLClassLoader cl;
        cl = new ChildFirstURLClassLoader(
                new URL[] { new URL("file://" + srcClassesDir), }, parentCl);

        refQueue = new ReferenceQueue();
        weakRef = new WeakReference(cl, refQueue);

        Class<?> clazz = cl.loadClass("io.sqreen.powerwaf.Powerwaf");
        Method initialize = clazz.getMethod("initialize", new Class[] { boolean.class });
        initialize.invoke(null, true);

        Method deinitialize = clazz.getMethod("deinitialize", new Class[0]);
        deinitialize.invoke(null);
    }

    @Test
    public void library_is_unloaded() throws Exception {
        testBody();

        System.gc();
        System.runFinalization();
        System.gc();

        Reference<? extends ClassLoader> poll = refQueue.poll();
        assertThat(poll, (Matcher) sameInstance(weakRef));
    }
}
