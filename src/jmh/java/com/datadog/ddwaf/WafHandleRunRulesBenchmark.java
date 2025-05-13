package com.datadog.ddwaf;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@Warmup(iterations = 1, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
public class WafHandleRunRulesBenchmark {

  private static final int OP_COUNT = 1024;

  private WafBuilder builder;
  private WafHandle handle;
  private WafContext context;
  private Waf.Limits limits;
  private Map<String, Object> simplePayload;

  @Setup(Level.Iteration)
  public void setup() throws Exception {
    Waf.initialize(System.getProperty("useReleaseBinaries") == null);

    Map<String, Object> rules = new HashMap<>();
    rules.put("version", "1.0");

    Map<String, Object> rule = new HashMap<>();
    rule.put("id", "arachni_rule");
    rule.put("name", "Arachni");
    rule.put("conditions", Collections.emptyList());
    rule.put("tags", Collections.singletonMap("type", "flow1"));
    rule.put("action", "record");

    rules.put("events", Collections.singleton(rule));

    WafConfig cfg = new WafConfig();
    builder = new WafBuilder(cfg);
    builder.addOrUpdateConfig("test-rules", rules);
    handle = builder.buildWafHandleInstance();
    context = new WafContext(handle);
    limits = new Waf.Limits(5, 20, 100, 200000, 0);

    simplePayload =
        Collections.singletonMap(
            "server.request.headers.no_cookies", Collections.singletonMap("user-agent", "Arachni"));
  }

  @TearDown(Level.Iteration)
  public void teardown() {
    context.close();
    handle.close();
    builder.close();
  }

  @Benchmark
  @OperationsPerInvocation(OP_COUNT)
  public void empty(final Blackhole bh) throws Exception {
    for (int i = 0; i < OP_COUNT; i++) {
      bh.consume(context.run(Collections.emptyMap(), limits, null));
    }
  }

  @Benchmark
  @OperationsPerInvocation(OP_COUNT)
  public void small(final Blackhole bh) throws Exception {
    for (int i = 0; i < OP_COUNT; i++) {
      bh.consume(context.run(simplePayload, limits, null));
    }
  }
}
