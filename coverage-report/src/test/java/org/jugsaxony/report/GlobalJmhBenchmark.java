package org.jugsaxony.report;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
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
public class GlobalJmhBenchmark {

    @Param({"1000"})
    public int size;

    private org.jugsaxony.demo0.FastHashMap<String, String> demo0Map;
    private org.jugsaxony.demo1.FastHashMap<String, String> demo1Map;
    private org.jugsaxony.demo2.FastHashMap<String, String> demo2Map;
    private org.jugsaxony.demo3.FastHashMap<String, String> demo3Map;
    private org.jugsaxony.demo4.FastHashMap<String, String> demo4Map;
    private org.jugsaxony.demo5.FastHashMap<String, String> demo5Map;
    private org.jugsaxony.demo6.FastHashMap<String, String> demo6Map;
    private org.jugsaxony.demo7.FastHashMap<String, String> demo7Map;
    private org.jugsaxony.demo8.FastHashMap<String, String> demo8Map;
    private Map<String, String> javaMap;

    private String[] existingKeys;
    private String[] missingKeys;
    private String[] values;
    private int keyIndex;

    @Setup(Level.Trial)
    public void setup() {
        demo0Map = new org.jugsaxony.demo0.FastHashMap<>();
        demo1Map = new org.jugsaxony.demo1.FastHashMap<>();
        demo2Map = new org.jugsaxony.demo2.FastHashMap<>();
        demo3Map = new org.jugsaxony.demo3.FastHashMap<>();
        demo4Map = new org.jugsaxony.demo4.FastHashMap<>();
        demo5Map = new org.jugsaxony.demo5.FastHashMap<>();
        demo6Map = new org.jugsaxony.demo6.FastHashMap<>();
        demo7Map = new org.jugsaxony.demo7.FastHashMap<>();
        demo8Map = new org.jugsaxony.demo8.FastHashMap<>();
        javaMap = new HashMap<>();

        existingKeys = new String[size];
        missingKeys = new String[size];
        values = new String[size];

        Random rnd = new Random(42);
        for (int i = 0; i < size; i++) {
            existingKeys[i] = "key_" + i + "_" + rnd.nextInt(1_000_000);
            missingKeys[i] = "miss_" + i + "_" + rnd.nextInt(1_000_000);
            values[i] = "val_" + i;

            demo0Map.put(existingKeys[i], values[i]);
            demo1Map.put(existingKeys[i], values[i]);
            demo2Map.put(existingKeys[i], values[i]);
            demo3Map.put(existingKeys[i], values[i]);
            demo4Map.put(existingKeys[i], values[i]);
            demo5Map.put(existingKeys[i], values[i]);
            demo6Map.put(existingKeys[i], values[i]);
            demo7Map.put(existingKeys[i], values[i]);
            demo8Map.put(existingKeys[i], values[i]);
            javaMap.put(existingKeys[i], values[i]);
        }
    }

    // ==========================================
    // GET HIT BENCHMARKS
    // ==========================================

    @Benchmark
    public void getHit_demo0(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo0Map.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHit_demo1(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo1Map.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHit_demo2(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo2Map.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHit_demo3(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo3Map.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHit_demo4(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo4Map.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHit_demo5(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo5Map.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHit_demo6(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo6Map.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHit_demo7(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo7Map.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHit_demo8(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo8Map.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHit_javaMap(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(javaMap.get(existingKeys[idx]));
    }

    // ==========================================
    // GET MISS BENCHMARKS
    // ==========================================

    @Benchmark
    public void getMiss_demo0(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo0Map.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMiss_demo1(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo1Map.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMiss_demo2(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo2Map.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMiss_demo3(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo3Map.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMiss_demo4(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo4Map.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMiss_demo5(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo5Map.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMiss_demo6(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo6Map.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMiss_demo7(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo7Map.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMiss_demo8(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo8Map.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMiss_javaMap(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(javaMap.get(missingKeys[idx]));
    }

    // ==========================================
    // PUT BENCHMARKS
    // ==========================================

    @Benchmark
    public void put_demo0(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo0Map.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void put_demo1(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo1Map.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void put_demo2(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo2Map.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void put_demo3(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo3Map.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void put_demo4(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo4Map.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void put_demo5(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo5Map.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void put_demo6(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo6Map.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void put_demo7(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo7Map.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void put_demo8(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo8Map.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void put_javaMap(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(javaMap.put(existingKeys[idx], values[idx]));
    }

    public static void main(String[] args) throws Exception {
        File rootDir = GlobalDashboardGenerator.findRootDir();
        File reportsDir = new File(rootDir, "target/reports");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }

        File jsonResult = new File(reportsDir, "jmh-results.json");

        boolean quick = false;
        for (String arg : args) {
            if ("--quick".equalsIgnoreCase(arg)) {
                quick = true;
            }
        }

        org.openjdk.jmh.runner.options.ChainedOptionsBuilder builder = new OptionsBuilder()
                .include(GlobalJmhBenchmark.class.getSimpleName())
                .resultFormat(ResultFormatType.JSON)
                .result(jsonResult.getAbsolutePath());

        if (quick) {
            builder.warmupIterations(1)
                   .warmupTime(org.openjdk.jmh.runner.options.TimeValue.milliseconds(500))
                   .measurementIterations(1)
                   .measurementTime(org.openjdk.jmh.runner.options.TimeValue.milliseconds(500))
        }

        Options opt = builder.build();
        new Runner(opt).run();

        // Generate visual and markdown reports in root target/reports
        GlobalJmhReportGenerator.generateReports(jsonResult, reportsDir);

        // Also copy into coverage-report/target/reports if present
        File covReports = new File(rootDir, "coverage-report/target/reports");
        if (covReports.exists()) {
            GlobalJmhReportGenerator.generateReports(jsonResult, covReports);
        }

        System.out.println("JMH full cross-project report generated in: " + reportsDir.getAbsolutePath());
    }
}
