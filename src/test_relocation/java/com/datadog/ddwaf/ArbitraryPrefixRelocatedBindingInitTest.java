/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2026 Datadog, Inc.
 */

package com.datadog.ddwaf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Initializes the binding relocated to shaded.org.slf4j, a prefix other than the datadog.slf4j used
 * by dd-java-agent, from a loader that can also see an unrelated org.slf4j (APPSEC-71631). Any
 * relocation prefix must work: the native init names no slf4j class, so every slf4j reference lives
 * in bytecode that the shading tool relocates.
 */
public class ArbitraryPrefixRelocatedBindingInitTest {

  @Test
  public void initializesWithArbitrarySlf4jPrefix() throws Exception {
    ClassLoader parent = ArbitraryPrefixRelocatedBindingInitTest.class.getClassLoader();
    URL bindingJar =
        new File(requiredProperty("relocatedBindingArbitraryPrefixJar")).toURI().toURL();
    URLClassLoader cl = new URLClassLoader(new URL[] {bindingJar}, parent);

    // If the parent saw the binding, parent-first delegation would load the unrelocated classes and
    // the test would pass without exercising the relocated binding.
    try {
      Class.forName("com.datadog.ddwaf.Waf", false, parent);
      fail("com.datadog.ddwaf.Waf must not be loadable from the test class loader");
    } catch (ClassNotFoundException expected) {
    }

    Class<?> orgLevel = Class.forName("org.slf4j.event.Level", false, cl);
    assertThat(
        "org.slf4j must come from the parent", orgLevel.getClassLoader(), sameInstance(parent));

    Class<?> infoToDebug = Class.forName("com.datadog.ddwaf.logging.InfoToDebugLogger", false, cl);
    assertThat(
        "InfoToDebugLogger must be defined by the relocated binding loader",
        infoToDebug.getClassLoader(),
        sameInstance((ClassLoader) cl));
    infoToDebug.getConstructor(Class.forName("shaded.org.slf4j.Logger", false, cl));
    assertNoConstructorTaking(infoToDebug, "org.slf4j.Logger", cl);
    assertNoConstructorTaking(infoToDebug, "datadog.slf4j.Logger", cl);

    Class<?> waf = Class.forName("com.datadog.ddwaf.Waf", true, cl);
    try {
      waf.getMethod("initialize", boolean.class).invoke(null, false);
    } catch (InvocationTargetException e) {
      throw new AssertionError(
          "Waf.initialize failed with the binding relocated to shaded.org.slf4j", e.getCause());
    }

    // Same semantics as BasicTests: Waf.version =~ Waf.LIB_VERSION (regex find).
    String libVersion = (String) waf.getField("LIB_VERSION").get(null);
    Method getVersion = waf.getMethod("getVersion");
    String version = (String) getVersion.invoke(null);
    assertTrue(
        "version " + version + " does not match " + libVersion,
        Pattern.compile(libVersion).matcher(version).find());
  }

  // A parameter type that is not loadable from cl counts as absent.
  private static void assertNoConstructorTaking(Class<?> clazz, String paramType, ClassLoader cl) {
    Class<?> param;
    try {
      param = Class.forName(paramType, false, cl);
    } catch (ClassNotFoundException absent) {
      return;
    }
    try {
      clazz.getConstructor(param);
      fail(clazz.getName() + " must not have a constructor taking " + paramType);
    } catch (NoSuchMethodException expected) {
    }
  }

  // Set by the testSlf4jRelocation Gradle task; skip instead of failing with an NPE elsewhere.
  private static String requiredProperty(String name) {
    String value = System.getProperty(name);
    assumeTrue(name + " is only set by the testSlf4jRelocation Gradle task", value != null);
    return value;
  }
}
