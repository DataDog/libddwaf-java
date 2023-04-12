/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;

import io.sqreen.powerwaf.test.ChildFirstURLClassLoader;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URL;
import org.junit.Test;

public class PowerwafGCTests {
    ReferenceQueue<ClassLoader> refQueue;
    WeakReference<ClassLoader> weakRef;

    private void testBody() throws Exception {
        ClassLoader parentCl = PowerwafGCTests.class.getClassLoader();

        String urlSrc = parentCl.getResource("io/sqreen/powerwaf/Powerwaf.class").getFile();
        int endOfDirSrc = urlSrc.indexOf("io/sqreen/");
        String srcClassesDir = urlSrc.substring(0, endOfDirSrc);

        ChildFirstURLClassLoader cl;
        cl = new ChildFirstURLClassLoader(
                new URL[] { new URL("file://" + srcClassesDir), }, parentCl);

        refQueue = new ReferenceQueue<>();
        weakRef = new WeakReference<>(cl, refQueue);

        Class<?> clazz = cl.loadClass("io.sqreen.powerwaf.Powerwaf");
        Method initialize = clazz.getMethod("initialize", boolean.class);
        boolean simpleInit = System.getProperty("useReleaseBinaries") == null;
        initialize.invoke(null, simpleInit);

        Method deinitialize = clazz.getMethod("deinitialize");
        deinitialize.invoke(null);
    }

    @Test
    public void library_is_unloaded() throws Exception {
        testBody();

        Reference<? extends ClassLoader> poll;
        int i = 0;
        while (true) {
            System.gc();
            System.runFinalization();
            System.gc();
            poll = refQueue.poll();
            if (poll != null) {
                break;
            }
            if (i++ < 10) {
                Thread.yield();
            } else {
                break;
            }
        }

        assertThat(poll, sameInstance(weakRef));
    }
}
