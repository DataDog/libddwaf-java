package io.sqreen.powerwaf;

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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Warmup(iterations = 1, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
public class PowerwafContextRunRulesBenchmark {

    private static final int OP_COUNT = 1024;

    private PowerwafContext ctx;
    private Powerwaf.Limits limits;
    private Map<String, Object> simplePayload;

    @Setup(Level.Iteration)
    public void setup() throws Exception {
        Powerwaf.initialize(false);

        Map<String, Object> rules = new HashMap<>();
        rules.put("version", "1.0");

        Map<String, Object> rule = new HashMap<>();
        rule.put("id", "arachni_rule");
        rule.put("name", "Arachni");
        rule.put("conditions", Collections.emptyList());
        rule.put("tags", Collections.singletonMap("type", "flow1"));
        rule.put("action", "record");

        rules.put("events", Collections.singleton(rule));

        PowerwafConfig cfg = new PowerwafConfig();
        ctx = new PowerwafContext("test", cfg, rules);

        limits = new Powerwaf.Limits(5, 20, 100, 200000, 0);

        simplePayload = Collections.singletonMap("server.request.headers.no_cookies", Collections.singletonMap("user-agent", "Arachni"));
    }

    @TearDown(Level.Iteration)
    public void teardown() {
        ctx.close();
    }

    @Benchmark
    @OperationsPerInvocation(OP_COUNT)
    public void empty(final Blackhole bh) throws Exception {
        for (int i = 0; i < OP_COUNT; i++) {
            bh.consume(ctx.runRules(Collections.emptyMap(), limits, null));
        }
    }

    @Benchmark
    @OperationsPerInvocation(OP_COUNT)
    public void small(final Blackhole bh) throws Exception {
        for (int i = 0; i < OP_COUNT; i++) {
            bh.consume(ctx.runRules(simplePayload, limits, null));
        }
    }
}