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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Initializes the unrelocated binding from a loader that can also see a relocated datadog.slf4j, as
 * an unshaded binding does when dd-java-agent is attached (APPSEC-71631). Guards against choosing
 * the slf4j signatures by probing datadog.slf4j first instead of the InfoToDebugLogger constructor.
 */
public class UnrelocatedBindingWithRelocatedSlf4jVisibleTest {

  @Test
  public void initializesWithDatadogSlf4jVisibleFromBindingLoader() throws Exception {
    ClassLoader parent = UnrelocatedBindingWithRelocatedSlf4jVisibleTest.class.getClassLoader();
    List<URL> urls = new ArrayList<>();
    for (String dir : requiredProperty("mainClassesDirs").split(File.pathSeparator)) {
      addIfExists(urls, dir);
    }
    String resourcesDir = requiredProperty("mainResourcesDir");
    assertTrue(
        "native_libs/ missing under " + resourcesDir,
        new File(resourcesDir, "native_libs").isDirectory());
    addIfExists(urls, resourcesDir);
    urls.add(new File(requiredProperty("relocatedSlf4jJar")).toURI().toURL());
    URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), parent);

    // If the parent saw the binding, parent-first delegation would load it from there instead.
    try {
      Class.forName("com.datadog.ddwaf.Waf", false, parent);
      fail("com.datadog.ddwaf.Waf must not be loadable from the test class loader");
    } catch (ClassNotFoundException expected) {
    }

    // Both slf4j packages are visible from the binding loader.
    Class<?> ddogLevel = Class.forName("datadog.slf4j.event.Level", false, cl);
    assertThat(
        "datadog.slf4j must come from the relocated slf4j jar",
        ddogLevel.getClassLoader(),
        sameInstance((ClassLoader) cl));
    Class<?> orgLevel = Class.forName("org.slf4j.event.Level", false, cl);
    assertThat(
        "org.slf4j must come from the parent", orgLevel.getClassLoader(), sameInstance(parent));

    Class<?> infoToDebug = Class.forName("com.datadog.ddwaf.logging.InfoToDebugLogger", false, cl);
    assertThat(
        "InfoToDebugLogger must be defined by the binding loader",
        infoToDebug.getClassLoader(),
        sameInstance((ClassLoader) cl));
    infoToDebug.getConstructor(Class.forName("org.slf4j.Logger", false, cl));
    try {
      infoToDebug.getConstructor(Class.forName("datadog.slf4j.Logger", false, cl));
      fail("InfoToDebugLogger must not have a constructor taking datadog.slf4j.Logger");
    } catch (NoSuchMethodException expected) {
    }

    Class<?> waf = Class.forName("com.datadog.ddwaf.Waf", true, cl);
    try {
      waf.getMethod("initialize", boolean.class).invoke(null, false);
    } catch (InvocationTargetException e) {
      throw new AssertionError("Waf.initialize failed with the unrelocated binding", e.getCause());
    }

    // Same semantics as BasicTests: Waf.version =~ Waf.LIB_VERSION (regex find).
    String libVersion = (String) waf.getField("LIB_VERSION").get(null);
    Method getVersion = waf.getMethod("getVersion");
    String version = (String) getVersion.invoke(null);
    assertTrue(
        "version " + version + " does not match " + libVersion,
        Pattern.compile(libVersion).matcher(version).find());
  }

  // File.toURI() only adds the trailing '/' URLClassLoader needs for directories that exist.
  private static void addIfExists(List<URL> urls, String dir) throws Exception {
    File f = new File(dir);
    if (f.isDirectory()) {
      urls.add(f.toURI().toURL());
    }
  }

  // Set by the testSlf4jRelocation Gradle task; skip instead of failing with an NPE elsewhere.
  private static String requiredProperty(String name) {
    String value = System.getProperty(name);
    assumeTrue(name + " is only set by the testSlf4jRelocation Gradle task", value != null);
    return value;
  }
}
