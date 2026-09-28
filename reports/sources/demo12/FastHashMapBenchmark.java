package org.jugsaxony.demo12;

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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Microbenchmark comparing {@link FastHashMap} (open addressing with linear
 * probing)
 * against standard {@link java.util.HashMap} (separate chaining with linked
 * nodes / red-black trees).
 *
 * <h2>Key Microbenchmarking Design Considerations:</h2>
 * <ul>
 * <li><b>Hot-path overhead elimination:</b> Instead of an expensive modulo
 * operation ({@code % size})
 * which emits an {@code idiv} instruction (15–25 CPU cycles) and branch checks
 * on every call,
 * we use power-of-two sizes and bitwise masking ({@code index++ & mask}). This
 * executes in 1 cycle
 * and prevents arithmetic overflow issues when the counter passes
 * {@link Integer#MAX_VALUE}.</li>
 *
 * <li><b>Single-threaded execution:</b> As this benchmark is strictly single-threaded,
 * the access counter ({@code index}) is kept directly in the benchmark state without
 * additional concurrency wrapping.</li>
 *
 * <li><b>Direct return instead of Blackhole:</b> For sub-10ns operations,
 * calling {@code Blackhole.consume()}
 * adds non-trivial method invocation overhead. Returning the value directly
 * allows the JMH harness to
 * sink the result using compiler intrinsics with zero overhead.</li>
 *
 * <li><b>Controlled pre-sizing:</b> Both maps are initialized with explicit
 * capacities in {@link #setup()}
 * to match the expected item count. This avoids measuring accidental load
 * factor differences caused by
 * different default growth thresholds (e.g. {@code FastHashMap} 0.5 vs
 * {@code HashMap} 0.75).</li>
 *
 * <li><b>Clear operation semantics:</b> Accessing existing keys with
 * {@code put} is labeled as an update
 * ({@code updateFastMap}), since all keys were pre-populated during trial
 * setup. It tests in-place
 * value replacement rather than new table insertion or resizing.</li>
 *
 * <li><b>Note on String key hash caching:</b> {@link String#hashCode()} caches
 * its hash internally after
 * the first invocation. Because all keys are inserted during setup, string hash
 * codes are already
 * cached before measurement begins. Note that {@link FastHashMap#get} calls
 * {@code hashCode()} repeatedly
 * during probing, which is cheap for {@code String} but would be costly for
 * objects without cached hashes.</li>
 * </ul>
 */
@BenchmarkMode({ Mode.Throughput, Mode.AverageTime })
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = { "-Xms2g", "-Xmx2g" })
@State(Scope.Benchmark)
public class FastHashMapBenchmark {

    /**
     * Powers of two allow branchless bitmasking: {@code (idx & mask)} instead of
     * {@code (idx % size)}.
     * This eliminates 10-25 cycles of hardware integer division latency from every
     * benchmark iteration.
     */
    @Param({ "8", "128", "1024", "8192", "65536" })
    public int size;

    private FastHashMap<String, String> fastMap;
    private Map<String, String> javaMap;

    private String[] existingKeys;
    private String[] missingKeys;
    private String[] values;
    private int mask;
    private int index;

    @Setup(Level.Trial)
    public void setup() {
        mask = size - 1;

        // Pre-size both maps so initial allocation and load factors are controlled and
        // comparable:
        // - FastHashMap capacity is calculated for fill factor 0.5f (if supported).
        // - HashMap capacity is calculated for default load factor 0.75f without
        // triggering a resize.
        fastMap = createFastMap(size);
        javaMap = new HashMap<>((int) Math.ceil(size / 0.75f) + 1);

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

    // ---------------------------------------------------------------------------------------------
    // Get Hits: Key exists in map
    // ---------------------------------------------------------------------------------------------

    @Benchmark
    public String getHitFastMap() {
        return fastMap.get(existingKeys[index++ & mask]);
    }

    @Benchmark
    public String getHitJavaMap() {
        return javaMap.get(existingKeys[index++ & mask]);
    }

    // ---------------------------------------------------------------------------------------------
    // Get Misses: Key does not exist in map (exercises probing until FREE_KEY /
    // null node)
    // ---------------------------------------------------------------------------------------------

    @Benchmark
    public String getMissFastMap() {
        return fastMap.get(missingKeys[index++ & mask]);
    }

    @Benchmark
    public String getMissJavaMap() {
        return javaMap.get(missingKeys[index++ & mask]);
    }

    // ---------------------------------------------------------------------------------------------
    // In-place Update: Key already exists, value is overwritten
    // Note: This benchmarks updating existing entries, not inserting new entries
    // into the map.
    // ---------------------------------------------------------------------------------------------

    @Benchmark
    public String updateFastMap() {
        int idx = index++ & mask;
        return fastMap.put(existingKeys[idx], values[idx]);
    }

    @Benchmark
    public String updateJavaMap() {
        int idx = index++ & mask;
        return javaMap.put(existingKeys[idx], values[idx]);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> FastHashMap<K, V> createFastMap(int size) {
        try {
            return (FastHashMap<K, V>) FastHashMap.class.getConstructor(int.class, float.class).newInstance(size, 0.5f);
        } catch (Exception e) {
            return new FastHashMap<>();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Main Runner
    // ---------------------------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        fixClasspathForFork();
        CommandLineOptions cmdOptions = new CommandLineOptions(args);
        Options opt = new OptionsBuilder()
                .parent(cmdOptions)
                .include(FastHashMapBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    private static void fixClasspathForFork() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl instanceof java.net.URLClassLoader ucl) {
            StringBuilder sb = new StringBuilder();
            String currentCp = System.getProperty("java.class.path", "");
            sb.append(currentCp);
            for (java.net.URL url : ucl.getURLs()) {
                if (!currentCp.contains(url.getPath())) {
                    if (sb.length() > 0) {
                        sb.append(java.io.File.pathSeparator);
                    }
                    sb.append(url.getPath());
                }
            }
            System.setProperty("java.class.path", sb.toString());
        }
    }
}
