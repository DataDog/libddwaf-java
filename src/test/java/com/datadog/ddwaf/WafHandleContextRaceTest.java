/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2026 Datadog, Inc.
 */
package com.datadog.ddwaf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class WafHandleContextRaceTest {
  private WafBuilder builder;

  @Before
  public void createBuilder() {
    builder = new WafBuilder();
  }

  @After
  public void closeBuilder() {
    builder.close();
  }

  @BeforeClass
  public static void initialize() throws Exception {
    // Match JNITrait: the native timeout is captured once when JNI is loaded.
    System.setProperty("DD_APPSEC_WAF_TIMEOUT", "500000");
    Waf.initialize(System.getProperty("useReleaseBinaries") == null);
  }

  private static Map<String, Object> entry(Object... pairs) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
    return map;
  }

  private static WafHandle newHandle(WafBuilder builder) throws Exception {
    Map<String, Object> condition =
        entry(
            "operator",
            "phrase_match",
            "parameters",
            entry(
                "inputs",
                Collections.singletonList(entry("address", "server.request.query")),
                "list",
                Collections.singletonList("attack")));
    Map<String, Object> rule =
        entry(
            "id",
            "test",
            "name",
            "test",
            "tags",
            entry("type", "test", "category", "test"),
            "conditions",
            Collections.singletonList(condition));
    builder.addOrUpdateConfig(
        "test", entry("version", "2.2", "rules", Collections.singletonList(rule)));
    return builder.buildWafHandleInstance();
  }

  private static Lock lock(WafHandle handle, String name) throws Exception {
    Field field = WafHandle.class.getDeclaredField(name);
    field.setAccessible(true);
    return (Lock) field.get(handle);
  }

  private static void awaitWaiting(Thread thread, FutureTask<?> task) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!task.isDone()
        && thread.getState() != Thread.State.WAITING
        && System.nanoTime() < deadline) Thread.yield();
    assertFalse("operation must be waiting for the handle lock", task.isDone());
    assertEquals(Thread.State.WAITING, thread.getState());
  }

  @Test
  public void nullHandleIsRejected() {
    try {
      new WafContext(null);
      fail("null handle must be rejected");
    } catch (IllegalArgumentException expected) {
      // Preserve the existing public exception type.
    }
  }

  @Test
  public void closedHandleIsRejectedBeforeNativeInitialization() throws Exception {

    WafHandle handle = newHandle(builder);
    handle.close();
    try {
      new WafContext(handle);
      fail("closed handle must be rejected");
    } catch (IllegalStateException expected) {
      assertEquals("This WafHandle is no longer online", expected.getMessage());
    }
    handle.close();
  }

  @Test
  public void waitingCreationRechecksHandleAfterClose() throws Exception {

    WafHandle handle = newHandle(builder);
    Lock writeLock = lock(handle, "writeLock");
    FutureTask<WafContext> creation = new FutureTask<>(() -> new WafContext(handle));
    Thread thread = new Thread(creation, "waf-creation-after-close-test");
    writeLock.lock();
    try {
      thread.start();
      awaitWaiting(thread, creation);
      handle.close(); // The write lock is reentrant; creation must recheck after it is released.
    } finally {
      writeLock.unlock();
      thread.join(10000);
      handle.close();
    }
    try {
      WafContext unexpected = creation.get(10, TimeUnit.SECONDS);
      unexpected.close();
      fail("waiting creation must reject the retired handle");
    } catch (ExecutionException expected) {
      assertTrue(expected.getCause() instanceof IllegalStateException);
    }
  }

  @Test
  public void closeWaitsForCreationAndCreatedContextSurvives() throws Exception {

    WafHandle handle = newHandle(builder);
    Lock readLock = lock(handle, "readLock");
    CountDownLatch acquired = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    Field field = WafHandle.class.getDeclaredField("readLock");
    field.setAccessible(true);
    // Pause context initialization immediately after the real read lock is acquired.
    field.set(handle, new PausingLock(readLock, acquired, finish));
    FutureTask<WafContext> creation = new FutureTask<>(() -> new WafContext(handle));
    FutureTask<Void> closing =
        new FutureTask<>(
            () -> {
              handle.close();
              return null;
            });
    Thread creator = new Thread(creation, "waf-protected-creation-test");
    Thread closer = new Thread(closing, "waf-handle-close-test");
    WafContext context = null;
    try {
      creator.start();
      assertTrue(acquired.await(10, TimeUnit.SECONDS));
      closer.start();
      awaitWaiting(closer, closing);
      assertTrue(handle.isOnline());
      finish.countDown();
      context = creation.get(10, TimeUnit.SECONDS);
      closing.get(10, TimeUnit.SECONDS);
      assertFalse(handle.isOnline());
      Waf.ResultWithData result =
          context.run(
              entry("server.request.query", entry("q", "attack")),
              new Waf.Limits(50, 500, 1000, 5000000, 5000000),
              null);
      assertEquals(Waf.Result.MATCH, result.result);
    } finally {
      finish.countDown();
      creator.join(10000);
      closer.join(10000);
      if (context == null && creation.isDone()) {
        try {
          context = creation.get();
        } catch (ExecutionException ignored) {
        }
      }
      if (context != null) context.close();
      handle.close();
    }
  }

  private static class PausingLock implements Lock {
    private final Lock delegate;
    private final CountDownLatch acquired;
    private final CountDownLatch finish;

    PausingLock(Lock delegate, CountDownLatch acquired, CountDownLatch finish) {
      this.delegate = delegate;
      this.acquired = acquired;
      this.finish = finish;
    }

    @Override
    public void lock() {
      delegate.lock();
      acquired.countDown();
      try {
        if (!finish.await(10, TimeUnit.SECONDS)) throw new AssertionError("creation timed out");
      } catch (InterruptedException e) {
        delegate.unlock();
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      } catch (AssertionError e) {
        delegate.unlock();
        throw e;
      }
    }

    @Override
    public void unlock() {
      delegate.unlock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
      delegate.lockInterruptibly();
    }

    @Override
    public boolean tryLock() {
      return delegate.tryLock();
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
      return delegate.tryLock(time, unit);
    }

    @Override
    public Condition newCondition() {
      return delegate.newCondition();
    }
  }
}
