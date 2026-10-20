/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2026 Datadog, Inc.
 */

package com.datadog.ddwaf

import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.lang.reflect.Field
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.LockSupport

import static groovy.test.GroovyAssert.shouldFail
import static org.junit.Assert.fail

@CompileStatic
class WafHandleContextRaceTest implements JNITrait {
  private WafBuilder builder

  @Before
  void setUp() {
    builder = new WafBuilder()
  }

  @After
  void closeBuilder() {
    builder.close()
  }

  @Test
  void nullHandleIsRejected() {
    shouldFail(IllegalArgumentException) {
      new WafContext(null)
    }
  }

  @Test
  void closedHandleIsRejectedBeforeNativeInitialization() {
    WafHandle handle = newHandle(builder)
    handle.close()
    try {
      new WafContext(handle)
      fail('closed handle must be rejected')
    } catch (IllegalStateException expected) {
      assert expected.message == 'This WafHandle is no longer online'
    }
    handle.close()
  }

  @Test
  void waitingCreationRechecksHandleAfterClose() {
    WafHandle handle = newHandle(builder)
    Lock writeLock = lock(handle, 'writeLock')
    FutureTask<WafContext> creation = new FutureTask<WafContext>({
      new WafContext(handle)
    } as Callable<WafContext>)
    Thread thread = new Thread(creation, 'waf-creation-after-close-test')
    writeLock.lock()
    try {
      thread.start()
      awaitWaiting(thread, creation)
      handle.close() // The write lock is reentrant; creation must recheck after it is released.
    } finally {
      writeLock.unlock()
      thread.join(10000)
      handle.close()
    }
    try {
      WafContext unexpected = creation.get(10, TimeUnit.SECONDS)
      unexpected.close()
      fail('waiting creation must reject the retired handle')
    } catch (ExecutionException expected) {
      assert expected.cause instanceof IllegalStateException
    }
  }

  @Test
  void newCreationIsRejectedWhileCloseWaitsForExistingReader() {
    WafHandle handle = newHandle(builder)
    Lock readLock = lock(handle, 'readLock')
    FutureTask<Void> closing = new FutureTask<Void>({
      handle.close()
      null
    } as Callable<Void>)
    FutureTask<Void> creation = new FutureTask<Void>({
      try (WafContext unexpected = new WafContext(handle)) {
        fail('new creation must reject a closing handle')
      } catch (IllegalStateException expected) {
        assert expected.message == 'This WafHandle is no longer online'
      }
      null
    } as Callable<Void>)
    Thread closer = new Thread(closing, 'waf-queued-close-test')
    Thread creator = new Thread(creation, 'waf-reject-creation-during-close-test')
    readLock.lock()
    try {
      closer.start()
      awaitWaiting(closer, closing)
      creator.start()
      // Must finish while close is still waiting, without releasing the existing reader.
      creation.get(10, TimeUnit.SECONDS)
      assert !closing.done: 'close must still be waiting for the existing reader'
      assert handle.online
    } finally {
      readLock.unlock()
      creator.join(10000)
      closer.join(10000)
      handle.close()
    }
    closing.get(10, TimeUnit.SECONDS)
    assert !handle.online
  }

  @Test
  void contextRemainsUsableAfterHandleClose() {
    WafHandle handle = newHandle(builder)
    try (WafContext context = new WafContext(handle)) {
      handle.close()
      assert !handle.online
      Waf.ResultWithData result = context.run(
      ['server.request.query': [q: 'attack']],
      new Waf.Limits(50, 500, 1000, 5000000, 5000000), null)
      assert result.result == Waf.Result.MATCH
    } finally {
      handle.close()
    }
  }

  private static WafHandle newHandle(WafBuilder builder) {
    Map<String, Object> rule = [
      id: 'test',
      name: 'test',
      tags: [type: 'test', category: 'test'],
      conditions: [
        [
          operator: 'phrase_match',
          parameters: [inputs: [[address: 'server.request.query']], list: ['attack']]
        ]
      ]
    ]
    builder.addOrUpdateConfig('test', [version: '2.2', rules: [rule]])
    builder.buildWafHandleInstance()
  }

  private static Lock lock(WafHandle handle, String name) {
    Field field = WafHandle.getDeclaredField(name)
    field.accessible = true
    (Lock) field.get(handle)
  }

  private static void awaitWaiting(Thread thread, FutureTask<?> task) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (!task.done
    && thread.state != Thread.State.WAITING
    && System.nanoTime() < deadline) {
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
    }
    assert !task.done: 'operation must be waiting for the handle lock'
    assert thread.state == Thread.State.WAITING
  }
}
