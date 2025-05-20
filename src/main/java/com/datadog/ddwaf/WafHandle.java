/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf;

import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WafHandle {
  private static final Logger LOGGER = LoggerFactory.getLogger(WafHandle.class);
  private final long nativeHandle;
  private boolean online;
  private final Lock writeLock;
  private final Lock readLock;
  private final String uniqueName;
  private final LeakDetection.PhantomRefWithName<Object> selfRef;

  // called from JNI
  private WafHandle(long handle) {
    if (handle == 0) {
      throw new IllegalArgumentException("Cannot build null WafHandles");
    }
    online = true;
    this.nativeHandle = handle;
    ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    this.readLock = rwLock.readLock();
    this.writeLock = rwLock.writeLock();
    this.uniqueName = UUID.randomUUID().toString();
    if (Waf.EXIT_ON_LEAK) {
      this.selfRef = LeakDetection.registerCloseable(this);
    } else {
      this.selfRef = null;
    }
    LOGGER.debug("Successfully create Waf handle {}", uniqueName);
  }

  private void checkIfOnline() {
    if (!this.online) {
      throw new IllegalStateException("This WafHandle is no longer online");
    }
  }

  public void close() {
    this.writeLock.lock();
    try {
      if (nativeHandle == 0 || !online) {
        return;
      }
      destroyWafHandle(this.nativeHandle);
      if (this.selfRef != null) {
        LeakDetection.notifyClose(this.selfRef);
      }
    } finally {
      online = false;
      this.writeLock.unlock();
    }
  }

  public boolean isOnline() {
    return online;
  }

  public String[] getKnownAddresses() {
    this.readLock.lock();
    try {
      checkIfOnline();
      return getKnownAddresses(this);
    } finally {
      this.readLock.unlock();
    }
  }

  public String[] getKnownActions() {
    this.readLock.lock();
    try {
      checkIfOnline();
      return getKnownActions(this);
    } finally {
      this.readLock.unlock();
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("WafHandle{");
    sb.append(uniqueName);
    sb.append('}');
    return sb.toString();
  }

  private static native void destroyWafHandle(long nativeWafHandle);

  private static native String[] getKnownAddresses(WafHandle handle);

  private static native String[] getKnownActions(WafHandle handle);
}
