/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import groovy.transform.CompileStatic
import org.junit.BeforeClass

@CompileStatic
trait JNITrait {

  @BeforeClass
  static void beforeClass() {
    boolean simpleInit = System.getProperty('useReleaseBinaries') == null
    System.setProperty('PW_RUN_TIMEOUT', '500000' /* 500 ms */)
    Waf.initialize(simpleInit)
  }

  // do not deinitialize. Even when running the tests in a separate classloader,
  // Groovy holds caches with soft references that prevent the classloader from
  // being garbage collect and its native library from being unloaded in the finalizer
  // Therefore, the library would be reloaded and reinitialized and would stay
  // uninitialized for subsequent tests
  //    @AfterClass
  //    static void afterClass() {
  //        Waf.deinitialize()
  //    }

}
