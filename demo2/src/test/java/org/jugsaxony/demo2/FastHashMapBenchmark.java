package org.jugsaxony.demo2;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class FastHashMapBenchmark {

    @Param({"100", "1000", "10000"})
    public int size;

    private FastHashMap<String, String> fastMap;
    private Map<String, String> javaMap;

    private String[] existingKeys;
    private String[] missingKeys;
    private String[] values;
    private int keyIndex;

    @Setup(Level.Trial)
    public void setup() {
        fastMap = new FastHashMap<>();
        javaMap = new HashMap<>();

        existingKeys = new String[size];
        missingKeys = new String[size];
        values = new String[size];

        Random rnd = new Random(42);
        for (int i = 0; i < size; i++) {
            existingKeys[i] = "key_" + i + "_" + rnd.nextInt(1_000_000);
            missingKeys[i] = "miss_" + i + "_" + rnd.nextInt(1_000_000);
            values[i] = "val_" + i;

            fastMap.put(existingKeys[i], values[i]);
            javaMap.put(existingKeys[i], values[i]);
        }
    }

    @Benchmark
    public void getHitFastMap(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(fastMap.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHitJavaMap(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(javaMap.get(existingKeys[idx]));
    }

    @Benchmark
    public void getMissFastMap(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(fastMap.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMissJavaMap(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(javaMap.get(missingKeys[idx]));
    }

    @Benchmark
    public void putFastMap(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(fastMap.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void putJavaMap(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(javaMap.put(existingKeys[idx], values[idx]));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(FastHashMapBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
