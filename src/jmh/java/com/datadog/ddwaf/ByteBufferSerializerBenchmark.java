package com.datadog.ddwaf;

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
import org.openjdk.jmh.annotations.Warmup;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Warmup(iterations = 1, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
public class ByteBufferSerializerBenchmark {

    private static final int OP_COUNT = 1024;

    private Map<String, Object> simplePayload;
    private ByteBufferSerializer serializer;

    @Setup(Level.Iteration)
    public void setup() throws Exception {
        Waf.initialize(System.getProperty("useReleaseBinaries") == null);

        Waf.Limits limits = new Waf.Limits(5, 20, 100, 200000, 0);
        serializer = new ByteBufferSerializer(limits);

        simplePayload = Collections.singletonMap("server.request.headers.no_cookies", Collections.singletonMap("user-agent", "Arachni"));
    }

    @Benchmark
    @OperationsPerInvocation(OP_COUNT)
    public void empty() {
        for (int i = 0; i < OP_COUNT; i++) {
            final ByteBufferSerializer.ArenaLease lease = serializer.serialize(Collections.emptyMap(), null);
            lease.close();
        }
    }

    @Benchmark
    @OperationsPerInvocation(OP_COUNT)
    public void small() {
        for (int i = 0; i < OP_COUNT; i++) {
            final ByteBufferSerializer.ArenaLease lease = serializer.serialize(simplePayload, null);
            lease.close();
        }
    }
}