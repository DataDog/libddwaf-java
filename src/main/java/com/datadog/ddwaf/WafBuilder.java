/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

package com.datadog.ddwaf;

import com.datadog.ddwaf.exception.AbstractWafException;
import com.datadog.ddwaf.exception.InvalidRuleSetException;
import com.datadog.ddwaf.exception.UnclassifiedWafException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WafBuilder {
  private static final Logger log = LoggerFactory.getLogger(WafBuilder.class);
  // The ptr field holds the pointer to PWAddContext and managed by PowerWAF
  private final long ptr; // KEEP THIS FIELD!
  private boolean online;

  public WafBuilder() {
    this(null);
  }

  public WafBuilder(WafConfig config) {
    online = true;
    config = config == null ? WafConfig.DEFAULT_CONFIG : config;
    this.ptr = initBuilder(config);
  }

  /**
   * Adds or updates a configuration file.
   *
   * @param path Path to the config.
   * @param config The configuration to add, update or remove.
   * @return The diagnostics of the configuration.
   * @throws InvalidRuleSetException if the config is invalid.
   * @throws UnclassifiedWafException if request is not valid.
   */
  public synchronized WafDiagnostics addOrUpdateConfig(String path, Map<String, Object> config)
      throws UnclassifiedWafException {
    if (!online) {
      throw new UnclassifiedWafException("WafBuilder is offline");
    }
    if (config == null) {
      throw new IllegalArgumentException("Config cannot be null");
    }
    if (path == null) {
      throw new IllegalArgumentException("Path cannot be null");
    }
    if (path.isEmpty()) {
      throw new IllegalArgumentException("Path cannot be empty");
    }
    WafDiagnostics[] infoRef = new WafDiagnostics[1];
    if (addOrUpdateConfigNative(this, path, config, infoRef)) {
      return infoRef[0];
    } else {
      throw new InvalidRuleSetException(infoRef[0], "Invalid WAF configuration");
    }
  }

  /**
   * Removes a configuration. It does not fail if the configuration does not exist.
   *
   * @param path Path to the configuration.
   * @throws UnclassifiedWafException If the builder is closed.
   * @throws IllegalArgumentException If the path is null or empty.
   */
  public synchronized void removeConfig(String path) throws UnclassifiedWafException {
    if (!online) {
      throw new UnclassifiedWafException("WafBuilder is offline");
    }
    if (path == null) {
      throw new IllegalArgumentException("Path cannot be null");
    }
    if (path.isEmpty()) {
      throw new IllegalArgumentException("Path cannot be empty");
    }
    if (!removeConfigNative(this, path)) {
      throw new UnclassifiedWafException("Failed to remove configuration");
    }
  }

  /**
   * Builds a new WafHandle instance that can be used for creating contexts
   *
   * @return The new WafHandle instance.
   * @throws AbstractWafException if the WafHandle cannot be built. Most likely cause is that there
   *     are no valid rules in the configurations.
   */
  public synchronized WafHandle buildWafHandleInstance() throws AbstractWafException {
    if (!online) {
      throw new UnclassifiedWafException("WafBuilder is offline");
    }
    WafHandle handle = buildInstance(this);
    if (handle == null) {
      throw new UnclassifiedWafException(
          "Failed to build WafHandle instance, "
              + "check rules to make sure there is at least one valid one");
    }
    return handle;
  }

  /** Closes the WafBuilder instance and frees the resources. */
  public synchronized void close() {
    if (!online) {
      return;
    }
    online = false;
    destroyBuilder(ptr);
  }

  /** Builds a new WafHandle. This method is NOT THREAD SAFE. */
  private static native WafHandle buildInstance(WafBuilder wafBuilder);

  private static native long initBuilder(WafConfig config);

  private static native boolean addOrUpdateConfigNative(
      WafBuilder wafBuilder, String path, Map<String, Object> definition, WafDiagnostics[] infoRef);

  private static native boolean removeConfigNative(WafBuilder wafBuilder, String oldPath);

  private static native void destroyBuilder(long builderPtr);

  public boolean isOnline() {
    return online;
  }
}
