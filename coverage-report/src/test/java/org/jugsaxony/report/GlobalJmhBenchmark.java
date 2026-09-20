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
    private org.jugsaxony.demo9.FastHashMap<String, String> demo9Map;
    private org.jugsaxony.demo11.FastHashMap<String, String> demo11Map;
    private org.jugsaxony.demo12.FastHashMap<String, String> demo12Map;

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
        demo9Map = new org.jugsaxony.demo9.FastHashMap<>();
        demo11Map = new org.jugsaxony.demo11.FastHashMap<>();
        demo12Map = new org.jugsaxony.demo12.FastHashMap<>();

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
            demo9Map.put(existingKeys[i], values[i]);
            demo11Map.put(existingKeys[i], values[i]);
            demo12Map.put(existingKeys[i], values[i]);
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
    public void getHit_demo9(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo9Map.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHit_demo11(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo11Map.get(existingKeys[idx]));
    }

    @Benchmark
    public void getHit_demo12(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo12Map.get(existingKeys[idx]));
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
    public void getMiss_demo9(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo9Map.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMiss_demo11(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo11Map.get(missingKeys[idx]));
    }

    @Benchmark
    public void getMiss_demo12(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo12Map.get(missingKeys[idx]));
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
    public void put_demo9(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo9Map.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void put_demo11(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo11Map.put(existingKeys[idx], values[idx]));
    }

    @Benchmark
    public void put_demo12(Blackhole bh) {
        int idx = (keyIndex++) % size;
        if (idx < 0) idx = -idx;
        bh.consume(demo12Map.put(existingKeys[idx], values[idx]));
    }

    public static void fixClasspathForFork() {
        String cp = System.getProperty("java.class.path");
        if (cp == null || !cp.contains("jmh-core")) {
            java.util.Set<String> paths = new java.util.LinkedHashSet<>();
            if (cp != null && !cp.isBlank()) {
                for (String part : cp.split(File.pathSeparator)) {
                    if (!part.isBlank()) paths.add(part);
                }
            }

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            while (cl != null) {
                if (cl instanceof java.net.URLClassLoader ucl) {
                    for (java.net.URL url : ucl.getURLs()) {
                        try {
                            paths.add(new File(url.toURI()).getAbsolutePath());
                        } catch (Exception ignored) {}
                    }
                }
                cl = cl.getParent();
            }

            cl = GlobalJmhBenchmark.class.getClassLoader();
            while (cl != null) {
                if (cl instanceof java.net.URLClassLoader ucl) {
                    for (java.net.URL url : ucl.getURLs()) {
                        try {
                            paths.add(new File(url.toURI()).getAbsolutePath());
                        } catch (Exception ignored) {}
                    }
                }
                cl = cl.getParent();
            }

            File rootDir = GlobalDashboardGenerator.findRootDir();
            File testClasses = new File(rootDir, "coverage-report/target/test-classes");
            if (testClasses.exists()) paths.add(testClasses.getAbsolutePath());
            File classes = new File(rootDir, "coverage-report/target/classes");
            if (classes.exists()) paths.add(classes.getAbsolutePath());

            if (!paths.isEmpty()) {
                System.setProperty("java.class.path", String.join(File.pathSeparator, paths));
            }
        }
    }

    public static boolean isPerfAvailable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) {
            return false;
        }
        try {
            Process p = new ProcessBuilder("perf", "--version").redirectErrorStream(true).start();
            boolean finished = p.waitFor(2, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        fixClasspathForFork();

        File rootDir = GlobalDashboardGenerator.findRootDir();
        File reportsDir = new File(rootDir, "target/reports");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }

        File jsonResult = new File(reportsDir, "jmh-results.json");

        boolean quick = false;
        boolean enablePerf = false;
        boolean enableGc = false;
        String filter = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--quick".equalsIgnoreCase(arg)) {
                quick = true;
            } else if ("--perf".equalsIgnoreCase(arg) || "--perfnorm".equalsIgnoreCase(arg) || "-perf".equalsIgnoreCase(arg)) {
                enablePerf = true;
            } else if ("--gc".equalsIgnoreCase(arg) || "-gc".equalsIgnoreCase(arg)) {
                enableGc = true;
            } else if ("--filter".equalsIgnoreCase(arg) && i + 1 < args.length) {
                filter = args[++i];
            } else if (arg.startsWith("--filter=")) {
                filter = arg.substring("--filter=".length());
            }
        }

        org.openjdk.jmh.runner.options.ChainedOptionsBuilder builder = new OptionsBuilder()
                .resultFormat(ResultFormatType.JSON)
                .result(jsonResult.getAbsolutePath());

        if (filter != null && !filter.isBlank()) {
            builder.include(filter);
        } else {
            builder.include(GlobalJmhBenchmark.class.getSimpleName());
        }

        if (quick) {
            builder.warmupIterations(1)
                   .warmupTime(org.openjdk.jmh.runner.options.TimeValue.milliseconds(500))
                   .measurementIterations(1)
                   .measurementTime(org.openjdk.jmh.runner.options.TimeValue.milliseconds(500));
        }

        if (enablePerf) {
            if (isPerfAvailable()) {
                System.out.println("Enabling LinuxPerfNormProfiler for hardware performance counter statistics...");
                builder.addProfiler(org.openjdk.jmh.profile.LinuxPerfNormProfiler.class);
            } else {
                System.err.println("WARNING: --perf requested, but Linux perf is not available on this system. Continuing without perf profiler.");
            }
        }

        if (enableGc) {
            System.out.println("Enabling GCProfiler for memory allocation statistics...");
            builder.addProfiler(org.openjdk.jmh.profile.GCProfiler.class);
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
