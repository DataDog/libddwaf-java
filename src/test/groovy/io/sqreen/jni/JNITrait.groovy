package io.sqreen.jni

import groovy.transform.CompileStatic
import io.sqreen.powerwaf.Powerwaf
import org.junit.BeforeClass

@CompileStatic
trait JNITrait {

    @BeforeClass
    static void beforeClass() {
        boolean simpleInit = System.getProperty('useReleaseBinaries') == null
        System.setProperty('PW_RUN_TIMEOUT', '500000' /* 500 ms */)
        Powerwaf.initialize(simpleInit)
    }

    // do not deinitialize. Even when running the tests in a separate classloader,
    // Groovy holds caches with soft references that prevent the classloader from
    // being garbage collect and its native library from being unloaded in the finalizer
    // Therefore, the library would be reloaded and reinitialized and would stay
    // uninitialized for subsequent tests
//    @AfterClass
//    static void afterClass() {
//        Powerwaf.deinitialize()
//    }

}
