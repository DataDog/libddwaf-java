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
    System.setProperty('DD_APPSEC_WAF_TIMEOUT', '500000' /* 500 ms */)
    Waf.initialize(simpleInit)
  }
}

