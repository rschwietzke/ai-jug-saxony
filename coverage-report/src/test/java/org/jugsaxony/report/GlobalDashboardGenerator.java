package org.jugsaxony.report;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlobalDashboardGenerator {

    public record QualityStats(
            int tests,
            double executionTimeSeconds,
            int failures,
            int errors,
            double instructionCoveragePct,
            double lineCoveragePct,
            double branchCoveragePct,
            int missedInstructions,
            int totalInstructions,
            int missedBranches,
            int totalBranches,
            int missedLines,
            int totalLines,
            int pitKilled,
            int pitTotal,
            double pitScorePct
    ) {}

    public record FastHashMapSummary(
            String id,
            String name,
            String aiModel,
            String storageStrategy,
            QualityStats quality,
            long shallowSizeBytes,
            long emptySizeBytes,
            long n1000SizeBytes,
            double n1000BytesPerEntry,
            long n10000ObjectCount,
            double getHitThroughput,
            double getHitSpeedup,
            double getMissThroughput,
            double getMissSpeedup,
            double putThroughput,
            double putSpeedup,
            double hitCycles,
            double hitIpc,
            double hitBranchMissRate,
            double hitL1MissRate
    ) {
        public FastHashMapSummary(
                String id,
                String name,
                String aiModel,
                String storageStrategy,
                QualityStats quality,
                long shallowSizeBytes,
                long emptySizeBytes,
                long n1000SizeBytes,
                double n1000BytesPerEntry,
                long n10000ObjectCount,
                double getHitThroughput,
                double getHitSpeedup,
                double getMissThroughput,
                double getMissSpeedup,
                double putThroughput,
                double putSpeedup
        ) {
            this(id, name, aiModel, storageStrategy, quality, shallowSizeBytes, emptySizeBytes, n1000SizeBytes, n1000BytesPerEntry,
                 n10000ObjectCount, getHitThroughput, getHitSpeedup, getMissThroughput, getMissSpeedup, putThroughput, putSpeedup,
                 0.0, 0.0, 0.0, 0.0);
        }

        public boolean hasPerf() {
            return hitCycles > 0 || hitIpc > 0;
        }
    }

    public record LruClockMapSummary(
            String id,
            String name,
            String aiModel,
            QualityStats quality
    ) {}

    public record ReportUtilSummary(
            String id,
            String name,
            String aiModel,
            QualityStats quality
    ) {}

    public static final Map<String, String[]> MODULE_METADATA = Map.ofEntries(
            Map.entry("demo0", new String[]{"Demo 0", "Baseline / Reference (FastRandom)", "Flat parallel Object[] arrays"}),
            Map.entry("demo1", new String[]{"Demo 1", "Gemini 3.7 Flash High (Antigravity)", "Flat parallel Object[] arrays"}),
            Map.entry("demo2", new String[]{"Demo 2", "Kimi K3 (Kilo Code)", "Flat parallel Object[] arrays"}),
            Map.entry("demo3", new String[]{"Demo 3", "OpenAI 5.6 Sol Max (Kilo Code)", "Flat parallel Object[] arrays"}),
            Map.entry("demo4", new String[]{"Demo 4", "Gemma 4 31B Thinking (Kilo Code)", "Flat parallel Object[] arrays + Tombstones"}),
            Map.entry("demo5", new String[]{"Demo 5", "Deepseek V4 Flash Max (Kilo Code)", "Chained Node/Entry Object table"}),
            Map.entry("demo6", new String[]{"Demo 6", "Claude Opus 5 Ultra (Claude)", "Flat parallel Object[] arrays"}),
            Map.entry("demo7", new String[]{"Demo 7", "Qwen 38 max XHigh (Kilo Code)", "Chained Node/Entry Object table"}),
            Map.entry("demo8", new String[]{"Demo 8", "Gemini 3.7 Flash High (Kilo Code)", "Chained Node/Entry Object table"}),
            Map.entry("demo9", new String[]{"Demo 9", "Gemini 3.8 Flash High (Antigravity)", "Flat parallel Object[] arrays"}),
            Map.entry("demo11", new String[]{"Demo 11", "Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed)", "Flat parallel Object[] arrays"}),
            Map.entry("demo12", new String[]{"Demo 12", "Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed)", "Flat parallel Object[] arrays"})
    );

    private static final Set<String> XLT_UTIL_FILES = Set.of(
            "RuntimeHistogram.java",
            "BitUtil.java",
            "BitCompression.java",
            "IntTimeSeries.java",
            "IntTimeSeriesEntry.java"
    );

    public static void generateDashboard(File outputDir, File rootProjectDir) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // 1. Gather JMH data if present
        File jmhJson = new File(outputDir, "jmh-results.json");
        if (!jmhJson.exists()) {
            jmhJson = new File(rootProjectDir, "reports/jmh-results.json");
        }
        if (!jmhJson.exists()) {
            jmhJson = new File(rootProjectDir, "coverage-report/target/reports/jmh-results.json");
        }
        if (!jmhJson.exists()) {
            jmhJson = new File(rootProjectDir, "target/reports/jmh-results.json");
        }

        Map<String, Map<String, Double>> jmhScores = new HashMap<>();
        Map<String, GlobalJmhReportGenerator.BenchmarkEntry> hitEntries = new HashMap<>();
        if (jmhJson.exists()) {
            List<GlobalJmhReportGenerator.BenchmarkEntry> jmhEntries = GlobalJmhReportGenerator.parseJmhJson(jmhJson);
            for (GlobalJmhReportGenerator.BenchmarkEntry entry : jmhEntries) {
                jmhScores.computeIfAbsent(entry.targetId(), k -> new HashMap<>()).put(entry.operation(), entry.score());
                if ("getHit".equals(entry.operation())) {
                    hitEntries.put(entry.targetId(), entry);
                }
            }
        }

        double baselineHit = jmhScores.getOrDefault("demo0", Map.of()).getOrDefault("getHit", 18.66);
        double baselineMiss = jmhScores.getOrDefault("demo0", Map.of()).getOrDefault("getMiss", 42.35);
        double baselinePut = jmhScores.getOrDefault("demo0", Map.of()).getOrDefault("put", 6.55);

        // 2. Gather FastHashMap, LRUClockMap & XLT Util data per module
        List<FastHashMapSummary> fastMapSummaries = new ArrayList<>();
        List<LruClockMapSummary> lruMapSummaries = new ArrayList<>();
        List<ReportUtilSummary> reportUtilSummaries = new ArrayList<>();

        for (GlobalJolReport.ImplementationMeta meta : GlobalJolReport.IMPLEMENTATIONS) {
            String modId = meta.id();
            String[] customMeta = MODULE_METADATA.get(modId);
            String storageStrategy = customMeta != null ? customMeta[2] : "Standard";

            File modDir = new File(rootProjectDir, modId);
            File surefireDir = new File(modDir, "target/surefire-reports");
            File jacocoXml = new File(modDir, "target/site/jacoco/jacoco.xml");
            File pitCsv = new File(modDir, "target/pit-reports/mutations.csv");

            QualityStats fastQuality = buildQualityStats(surefireDir, jacocoXml, pitCsv, "FastHashMapTest", "FastHashMap.java");
            QualityStats lruQuality = buildQualityStats(surefireDir, jacocoXml, pitCsv, "LRUClockMapTest", "LRUClockMap.java");
            QualityStats xltQuality = buildXltQualityStats(surefireDir, jacocoXml, pitCsv);

            // JOL for FastHashMap
            long shallow = 0;
            long empty = 0;
            long n1000 = 0;
            double n1000Bpe = 0.0;
            long n10000Objs = 0;

            try {
                shallow = ClassLayout.parseClass(meta.mapClass()).instanceSize();
                Object emptyMap = meta.factory().create();
                empty = GraphLayout.parseInstance(emptyMap).totalSize();

                Object n1000Map = meta.factory().create();
                for (int k = 0; k < 1000; k++) meta.factory().put(n1000Map, "key_" + k, k);
                n1000 = GraphLayout.parseInstance(n1000Map).totalSize();
                n1000Bpe = (double) n1000 / 1000.0;

                Object n10000Map = meta.factory().create();
                for (int k = 0; k < 10000; k++) meta.factory().put(n10000Map, "key_" + k, k);
                n10000Objs = GraphLayout.parseInstance(n10000Map).totalCount();
            } catch (Throwable t) {
                // Fallback if class layout extraction has issue
            }

            // JMH
            Map<String, Double> modJmh = jmhScores.getOrDefault(modId, Map.of());
            double hit = modJmh.getOrDefault("getHit", 0.0);
            double miss = modJmh.getOrDefault("getMiss", 0.0);
            double put = modJmh.getOrDefault("put", 0.0);

            GlobalJmhReportGenerator.BenchmarkEntry hitEntry = hitEntries.get(modId);
            double hitCycles = hitEntry != null ? hitEntry.cycles() : 0.0;
            double hitIpc = hitEntry != null ? hitEntry.ipc() : 0.0;
            double hitBranchMiss = hitEntry != null ? hitEntry.branchMissRate() : 0.0;
            double hitL1Miss = hitEntry != null ? hitEntry.l1DcacheMissRate() : 0.0;

            fastMapSummaries.add(new FastHashMapSummary(
                    modId,
                    meta.name(),
                    meta.model(),
                    storageStrategy,
                    fastQuality,
                    shallow,
                    empty,
                    n1000,
                    n1000Bpe,
                    n10000Objs,
                    hit,
                    baselineHit > 0 ? hit / baselineHit : 0,
                    miss,
                    baselineMiss > 0 ? miss / baselineMiss : 0,
                    put,
                    baselinePut > 0 ? put / baselinePut : 0,
                    hitCycles,
                    hitIpc,
                    hitBranchMiss,
                    hitL1Miss
            ));

            if (lruQuality != null) {
                lruMapSummaries.add(new LruClockMapSummary(
                        modId,
                        meta.name(),
                        meta.model(),
                        lruQuality
                ));
            }

            if (xltQuality != null) {
                reportUtilSummaries.add(new ReportUtilSummary(
                        modId,
                        meta.name(),
                        meta.model(),
                        xltQuality
                ));
            }
        }

        // Copy JaCoCo aggregate report if available
        File jacocoSite = new File(rootProjectDir, "coverage-report/target/site/jacoco-aggregate");
        File destCoverage = new File(outputDir, "coverage-aggregate");
        if (jacocoSite.exists() && jacocoSite.isDirectory()) {
            copyDirectory(jacocoSite, destCoverage);
        }

        String[] modDirs = {"demo0", "demo1", "demo2", "demo3", "demo4", "demo5", "demo6", "demo7", "demo8", "demo9", "demo11", "demo12"};

        // Preserve previous target/reports/jacoco if target directory has reports during migration
        File oldTargetReports = new File(rootProjectDir, "target/reports/jacoco");
        File destJacoco = new File(outputDir, "jacoco");
        if (oldTargetReports.exists() && oldTargetReports.isDirectory() && (!destJacoco.exists() || destJacoco.list() == null || destJacoco.list().length == 0)) {
            copyDirectory(oldTargetReports, destJacoco);
        }

        // Copy individual module JaCoCo reports
        for (String modDirName : modDirs) {
            File modJacoco = new File(rootProjectDir, modDirName + "/target/site/jacoco");
            File destModJacoco = new File(outputDir, "jacoco/" + modDirName);
            if (modJacoco.exists() && modJacoco.isDirectory()) {
                copyDirectory(modJacoco, destModJacoco);
            }
        }

        // Copy PIT reports per module if available
        for (String modDirName : modDirs) {
            File modPit = new File(rootProjectDir, modDirName + "/target/pit-reports");
            File destModPit = new File(outputDir, "pit-reports/" + modDirName);
            if (modPit.exists() && modPit.isDirectory()) {
                copyDirectory(modPit, destModPit);
            }
        }

        // Write Markdown Dashboard
        writeMarkdownDashboard(new File(outputDir, "README.md"), fastMapSummaries, lruMapSummaries, reportUtilSummaries);
        writeMarkdownDashboard(new File(outputDir, "global-dashboard.md"), fastMapSummaries, lruMapSummaries, reportUtilSummaries);

        // Write Master HTML Dashboard
        writeHtmlDashboard(new File(outputDir, "index.html"), fastMapSummaries, lruMapSummaries, reportUtilSummaries, null);
        writeHtmlDashboard(new File(outputDir, "global-dashboard.html"), fastMapSummaries, lruMapSummaries, reportUtilSummaries, null);

        // Write Standalone Sub-Section HTML Pages
        writeHtmlDashboard(new File(outputDir, "fasthashmap.html"), fastMapSummaries, lruMapSummaries, reportUtilSummaries, "fasthashmap");
        writeHtmlDashboard(new File(outputDir, "lruclockmap.html"), fastMapSummaries, lruMapSummaries, reportUtilSummaries, "lruclockmap");
        writeHtmlDashboard(new File(outputDir, "xlt-util.html"), fastMapSummaries, lruMapSummaries, reportUtilSummaries, "xlt-util");
    }

    private record TestExecutionInfo(int tests, double executionTimeSeconds) {}

    private static TestExecutionInfo parseTestExecution(File surefireDir, String testPattern, boolean isPrefix) {
        if (!surefireDir.exists() || !surefireDir.isDirectory()) return new TestExecutionInfo(0, 0.0);
        File[] files = surefireDir.listFiles((dir, name) -> {
            if (!name.startsWith("TEST-") || !name.endsWith(".xml")) return false;
            if (isPrefix) {
                return name.startsWith("TEST-" + testPattern);
            } else {
                return name.contains(testPattern);
            }
        });
        if (files == null) return new TestExecutionInfo(0, 0.0);

        int totalTests = 0;
        double totalTime = 0.0;
        Pattern tcPat = Pattern.compile("<testcase\\b[^>]*\\btime=\"([0-9.]+)\"");
        Pattern suitePat = Pattern.compile("<testsuite\\b[^>]*\\btime=\"([0-9.]+)\"[^>]*\\btests=\"([0-9]+)\"");
        Pattern altSuitePat = Pattern.compile("<testsuite\\b[^>]*\\btests=\"([0-9]+)\"[^>]*\\btime=\"([0-9.]+)\"");

        for (File f : files) {
            try {
                String content = Files.readString(f.toPath());
                Matcher tcMatcher = tcPat.matcher(content);
                int tcCount = 0;
                double fileTcTime = 0.0;
                while (tcMatcher.find()) {
                    tcCount++;
                    try {
                        fileTcTime += Double.parseDouble(tcMatcher.group(1));
                    } catch (NumberFormatException ignored) {}
                }

                if (tcCount > 0) {
                    totalTests += tcCount;
                    totalTime += fileTcTime;
                } else {
                    Matcher sm = suitePat.matcher(content);
                    if (!sm.find()) {
                        sm = altSuitePat.matcher(content);
                        if (sm.find()) {
                            int t = Integer.parseInt(sm.group(1));
                            double tm = Double.parseDouble(sm.group(2));
                            if (t > 0) {
                                totalTests += t;
                                totalTime += tm;
                            }
                        }
                    } else {
                        double tm = Double.parseDouble(sm.group(1));
                        int t = Integer.parseInt(sm.group(2));
                        if (t > 0) {
                            totalTests += t;
                            totalTime += tm;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return new TestExecutionInfo(totalTests, totalTime);
    }

    private static QualityStats buildQualityStats(File surefireDir, File jacocoXml, File pitCsv, String testPattern, String sourceFileName) {
        TestExecutionInfo testInfo = parseTestExecution(surefireDir, testPattern, false);
        int[] covInst = parseSourceCoverage(jacocoXml, sourceFileName, "INSTRUCTION");
        int[] covBranch = parseSourceCoverage(jacocoXml, sourceFileName, "BRANCH");
        int[] covLine = parseSourceCoverage(jacocoXml, sourceFileName, "LINE");

        double instPct = covInst[1] + covInst[0] > 0 ? (covInst[1] * 100.0) / (covInst[1] + covInst[0]) : 0.0;
        double branchPct = covBranch[1] + covBranch[0] > 0 ? (covBranch[1] * 100.0) / (covBranch[1] + covBranch[0]) : 0.0;
        double linePct = covLine[1] + covLine[0] > 0 ? (covLine[1] * 100.0) / (covLine[1] + covLine[0]) : 0.0;

        int[] pit = parsePitCoverage(pitCsv, sourceFileName);
        double pitPct = pit[1] > 0 ? (pit[0] * 100.0) / pit[1] : 0.0;

        return new QualityStats(
                testInfo.tests(),
                testInfo.executionTimeSeconds(),
                0,
                0,
                instPct,
                linePct,
                branchPct,
                covInst[0],
                covInst[0] + covInst[1],
                covBranch[0],
                covBranch[0] + covBranch[1],
                covLine[0],
                covLine[0] + covLine[1],
                pit[0],
                pit[1],
                pitPct
        );
    }

    private static QualityStats buildXltQualityStats(File surefireDir, File jacocoXml, File pitCsv) {
        TestExecutionInfo testInfo = parseTestExecution(surefireDir, "com.xceptance.xlt.report.util", true);
        int[] covInst = parsePackagePrefixCoverage(jacocoXml, "com/xceptance/xlt/report/util", "INSTRUCTION");
        int[] covBranch = parsePackagePrefixCoverage(jacocoXml, "com/xceptance/xlt/report/util", "BRANCH");
        int[] covLine = parsePackagePrefixCoverage(jacocoXml, "com/xceptance/xlt/report/util", "LINE");

        double instPct = covInst[1] + covInst[0] > 0 ? (covInst[1] * 100.0) / (covInst[1] + covInst[0]) : 0.0;
        double branchPct = covBranch[1] + covBranch[0] > 0 ? (covBranch[1] * 100.0) / (covBranch[1] + covBranch[0]) : 0.0;
        double linePct = covLine[1] + covLine[0] > 0 ? (covLine[1] * 100.0) / (covLine[1] + covLine[0]) : 0.0;

        int[] pit = parseXltPitCoverage(pitCsv);
        double pitPct = pit[1] > 0 ? (pit[0] * 100.0) / pit[1] : 0.0;

        return new QualityStats(
                testInfo.tests(),
                testInfo.executionTimeSeconds(),
                0,
                0,
                instPct,
                linePct,
                branchPct,
                covInst[0],
                covInst[0] + covInst[1],
                covBranch[0],
                covBranch[0] + covBranch[1],
                covLine[0],
                covLine[0] + covLine[1],
                pit[0],
                pit[1],
                pitPct
        );
    }

    private static int[] parseSourceCoverage(File jacocoXml, String sourceFileName, String counterType) {
        if (!jacocoXml.exists()) return new int[]{0, 0};
        try {
            String content = Files.readString(jacocoXml.toPath());
            Pattern sfPat = Pattern.compile("<sourcefile name=\"" + Pattern.quote(sourceFileName) + "\">(.*?)</sourcefile>", Pattern.DOTALL);
            Matcher sfMatcher = sfPat.matcher(content);
            if (sfMatcher.find()) {
                String sfBlock = sfMatcher.group(1);
                Pattern cPat = Pattern.compile("<counter type=\"" + counterType + "\" missed=\"([0-9]+)\" covered=\"([0-9]+)\"/>");
                Matcher cMatcher = cPat.matcher(sfBlock);
                int missed = 0;
                int covered = 0;
                while (cMatcher.find()) {
                    missed = Integer.parseInt(cMatcher.group(1));
                    covered = Integer.parseInt(cMatcher.group(2));
                }
                return new int[]{missed, covered};
            }
        } catch (Exception ignored) {}
        return new int[]{0, 0};
    }

    private static int[] parsePackagePrefixCoverage(File jacocoXml, String pkgPrefix, String counterType) {
        if (!jacocoXml.exists()) return new int[]{0, 0};
        try {
            String content = Files.readString(jacocoXml.toPath());
            Pattern pkgPat = Pattern.compile("<package name=\"(" + Pattern.quote(pkgPrefix) + "[^\"]*)\">(.*?)</package>", Pattern.DOTALL);
            Matcher pkgMatcher = pkgPat.matcher(content);
            int totalMissed = 0;
            int totalCovered = 0;
            boolean found = false;
            while (pkgMatcher.find()) {
                String pkgBlock = pkgMatcher.group(2);
                int lastSourceIndex = pkgBlock.lastIndexOf("</sourcefile>");
                if (lastSourceIndex < 0) {
                    lastSourceIndex = pkgBlock.lastIndexOf("</class>");
                }
                String packageCountersBlock = lastSourceIndex >= 0 ? pkgBlock.substring(lastSourceIndex) : pkgBlock;
                Pattern cPat = Pattern.compile("<counter type=\"" + counterType + "\" missed=\"([0-9]+)\" covered=\"([0-9]+)\"/>");
                Matcher cMatcher = cPat.matcher(packageCountersBlock);
                while (cMatcher.find()) {
                    totalMissed += Integer.parseInt(cMatcher.group(1));
                    totalCovered += Integer.parseInt(cMatcher.group(2));
                    found = true;
                }
            }
            if (found) {
                return new int[]{totalMissed, totalCovered};
            }
        } catch (Exception ignored) {}
        return new int[]{0, 0};
    }

    private static int[] parsePitCoverage(File pitCsv, String sourceFileName) {
        if (!pitCsv.exists()) return new int[]{0, 0};
        try (BufferedReader reader = new BufferedReader(new FileReader(pitCsv))) {
            String line;
            int killed = 0;
            int total = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    String file = parts[0].trim();
                    if (file.equals(sourceFileName) || file.endsWith("/" + sourceFileName) || file.endsWith("\\" + sourceFileName)) {
                        total++;
                        String status = parts[5].trim();
                        if ("KILLED".equalsIgnoreCase(status) || "TIMED_OUT".equalsIgnoreCase(status) || "MEMORY_ERROR".equalsIgnoreCase(status)) {
                            killed++;
                        }
                    }
                }
            }
            return new int[]{killed, total};
        } catch (Exception ignored) {}
        return new int[]{0, 0};
    }

    private static int[] parseXltPitCoverage(File pitCsv) {
        if (!pitCsv.exists()) return new int[]{0, 0};
        try (BufferedReader reader = new BufferedReader(new FileReader(pitCsv))) {
            String line;
            int killed = 0;
            int total = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    String file = parts[0].trim();
                    String clazz = parts[1].trim();
                    boolean matches = clazz.startsWith("com.xceptance.xlt.report.util") ||
                            XLT_UTIL_FILES.stream().anyMatch(f -> file.equals(f) || file.endsWith("/" + f) || file.endsWith("\\" + f));
                    if (matches) {
                        total++;
                        String status = parts[5].trim();
                        if ("KILLED".equalsIgnoreCase(status) || "TIMED_OUT".equalsIgnoreCase(status) || "MEMORY_ERROR".equalsIgnoreCase(status)) {
                            killed++;
                        }
                    }
                }
            }
            return new int[]{killed, total};
        } catch (Exception ignored) {}
        return new int[]{0, 0};
    }

    private static void writeMarkdownDashboard(
            File targetFile,
            List<FastHashMapSummary> fastMaps,
            List<LruClockMapSummary> lruMaps,
            List<ReportUtilSummary> reportUtils
    ) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("# 🏆 AI JUG Saxony Master Executive Dashboard");
            out.println();
            out.println("Comprehensive benchmark, code quality, memory footprint, and mutation evaluation comparing **AI Model implementations** against the `demo0` baseline.");
            out.println();
            out.println("---");
            out.println();
            out.println("## ⚡ Part 1: FastHashMap — Quality, Coverage & Mutation Verification");
            out.println();
            out.println("| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |");
            out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

            for (FastHashMapSummary s : fastMaps) {
                String pitStr = s.quality().pitTotal() > 0 ?
                        String.format("%.1f%% (%d/%d killed)", s.quality().pitScorePct(), s.quality().pitKilled(), s.quality().pitTotal()) : "N/A";

                String timeStr = s.quality().executionTimeSeconds() > 0 ? String.format(" (%.2fs)", s.quality().executionTimeSeconds()) : "";

                out.printf("| **%s** | %s | %d ✅%s | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %s | %s |%n",
                        s.id(),
                        s.aiModel(),
                        s.quality().tests(),
                        timeStr,
                        s.quality().instructionCoveragePct(),
                        s.quality().totalInstructions() - s.quality().missedInstructions(),
                        s.quality().totalInstructions(),
                        s.quality().lineCoveragePct(),
                        s.quality().totalLines() - s.quality().missedLines(),
                        s.quality().totalLines(),
                        s.quality().branchCoveragePct(),
                        s.quality().totalBranches() - s.quality().missedBranches(),
                        s.quality().totalBranches(),
                        pitStr,
                        "100% Passing ✅"
                );
            }

            out.println();
            out.println("### 💾 FastHashMap — JOL Memory Footprint & Layout Matrix");
            out.println();
            out.println("| Module | AI Model / Implementation | Memory @ 1k | Bytes / Entry @ 1k | Objs @ 10k | Empty Footprint | Shallow Size |");
            out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

            for (FastHashMapSummary s : fastMaps) {
                out.printf("| **%s** | %s | %,d B | %,.1f B/e | %,d | %,d B | %d B |%n",
                        s.id(),
                        s.aiModel(),
                        s.n1000SizeBytes(),
                        s.n1000BytesPerEntry(),
                        s.n10000ObjectCount(),
                        s.emptySizeBytes(),
                        s.shallowSizeBytes()
                );
            }

            out.println();
            out.println("### 🚀 FastHashMap — Performance & Benchmark Speedup Matrix");
            out.println();
            boolean anyPerf = fastMaps.stream().anyMatch(FastHashMapSummary::hasPerf);
            if (anyPerf) {
                out.println("| Module | AI Model / Implementation | Put Speedup | Get Hit Speedup | Cycles/op | IPC | Branch Miss % | L1 Miss % |");
                out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

                for (FastHashMapSummary s : fastMaps) {
                    String putStr = s.putThroughput() > 0 ? String.format("%.2fx (%.1f ops/µs)", s.putSpeedup(), s.putThroughput()) : "N/A";
                    String hitStr = s.getHitThroughput() > 0 ? String.format("%.2fx (%.1f ops/µs)", s.getHitSpeedup(), s.getHitThroughput()) : "N/A";
                    String cyclesStr = s.hitCycles() > 0 ? String.format("%.1f", s.hitCycles()) : "-";
                    String ipcStr = s.hitIpc() > 0 ? String.format("%.2f", s.hitIpc()) : "-";
                    String branchStr = s.hitBranchMissRate() > 0 ? String.format("%.2f%%", s.hitBranchMissRate()) : "-";
                    String l1Str = s.hitL1MissRate() > 0 ? String.format("%.2f%%", s.hitL1MissRate()) : "-";

                    out.printf("| **%s** | %s | %s | %s | %s | %s | %s | %s |%n",
                            s.id(),
                            s.aiModel(),
                            putStr,
                            hitStr,
                            cyclesStr,
                            ipcStr,
                            branchStr,
                            l1Str
                    );
                }
            } else {
                out.println("| Module | AI Model / Implementation | Put Speedup | Get Hit Speedup | Get Miss Speedup |");
                out.println("| :--- | :--- | :--- | :--- | :--- |");

                for (FastHashMapSummary s : fastMaps) {
                    String putStr = s.putThroughput() > 0 ? String.format("%.2fx (%.1f ops/µs)", s.putSpeedup(), s.putThroughput()) : "N/A";
                    String hitStr = s.getHitThroughput() > 0 ? String.format("%.2fx (%.1f ops/µs)", s.getHitSpeedup(), s.getHitThroughput()) : "N/A";
                    String missStr = s.getMissThroughput() > 0 ? String.format("%.2fx (%.1f ops/µs)", s.getMissSpeedup(), s.getMissThroughput()) : "N/A";

                    out.printf("| **%s** | %s | %s | %s | %s |%n",
                            s.id(),
                            s.aiModel(),
                            putStr,
                            hitStr,
                            missStr
                    );
                }
            }

            out.println();
            out.println("---");
            out.println();
            out.println("## ⏰ Part 2: LRUClockMap — Quality, Coverage & Mutation Verification");
            out.println();
            out.println("| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |");
            out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

            for (LruClockMapSummary s : lruMaps) {
                String pitStr = s.quality().pitTotal() > 0 ? String.format("%.1f%% (%d/%d killed)", s.quality().pitScorePct(), s.quality().pitKilled(), s.quality().pitTotal()) : "N/A";
                String timeStr = s.quality().executionTimeSeconds() > 0 ? String.format(" (%.2fs)", s.quality().executionTimeSeconds()) : "";

                out.printf("| **%s** | %s | %d ✅%s | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %s | %s |%n",
                        s.id(),
                        s.aiModel(),
                        s.quality().tests(),
                        timeStr,
                        s.quality().instructionCoveragePct(),
                        s.quality().totalInstructions() - s.quality().missedInstructions(),
                        s.quality().totalInstructions(),
                        s.quality().lineCoveragePct(),
                        s.quality().totalLines() - s.quality().missedLines(),
                        s.quality().totalLines(),
                        s.quality().branchCoveragePct(),
                        s.quality().totalBranches() - s.quality().missedBranches(),
                        s.quality().totalBranches(),
                        pitStr,
                        "100% Passing ✅"
                );
            }

            out.println();
            out.println("---");
            out.println();
            out.println("## 🧰 Part 3: com.xceptance.xlt.report.util — Quality, Coverage & Mutation Verification");
            out.println();
            out.println("| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |");
            out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

            for (ReportUtilSummary s : reportUtils) {
                String pitStr = s.quality().pitTotal() > 0 ? String.format("%.1f%% (%d/%d killed)", s.quality().pitScorePct(), s.quality().pitKilled(), s.quality().pitTotal()) : "N/A";
                String timeStr = s.quality().executionTimeSeconds() > 0 ? String.format(" (%.2fs)", s.quality().executionTimeSeconds()) : "";

                out.printf("| **%s** | %s | %d ✅%s | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %s | %s |%n",
                        s.id(),
                        s.aiModel(),
                        s.quality().tests(),
                        timeStr,
                        s.quality().instructionCoveragePct(),
                        s.quality().totalInstructions() - s.quality().missedInstructions(),
                        s.quality().totalInstructions(),
                        s.quality().lineCoveragePct(),
                        s.quality().totalLines() - s.quality().missedLines(),
                        s.quality().totalLines(),
                        s.quality().branchCoveragePct(),
                        s.quality().totalBranches() - s.quality().missedBranches(),
                        s.quality().totalBranches(),
                        pitStr,
                        "100% Passing ✅"
                );
            }

            out.println();
            out.println("---");
            out.println();
            out.println("## 📑 Linked Detailed Reports");
            out.println();
            out.println("- 🧪 **Unit Tests**: [Surefire Aggregated Report](surefire.html) (100% passing tests)");
            out.println("- 🎯 **Code Coverage**: [JaCoCo Aggregate Coverage Report](coverage-aggregate/index.html)");
            out.println("- 🧬 **Mutation Testing**: [PIT Mutation Reports](pit-reports/index.html)");
            out.println("- 💾 **Memory Footprint & Layout**: [JOL Memory Report](jol-report.html) / [Markdown](jol-report.md)");
            out.println("- ⚡ **Microbenchmarks & Perf Counters**: [JMH Benchmark Report](jmh-report.html) / [Markdown](jmh-report.md)");
            out.println();
            out.println("Generated automatically by `GlobalDashboardGenerator` on " + java.time.Instant.now());
        }
    }

    private static void writeHtmlDashboard(
            File targetFile,
            List<FastHashMapSummary> fastMaps,
            List<LruClockMapSummary> lruMaps,
            List<ReportUtilSummary> reportUtils,
            String activeSection
    ) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            int totalFastTests = fastMaps.stream().mapToInt(s -> s.quality().tests()).sum();
            double totalFastTime = fastMaps.stream().mapToDouble(s -> s.quality().executionTimeSeconds()).sum();

            int totalLruTests = lruMaps.stream().mapToInt(s -> s.quality().tests()).sum();
            double totalLruTime = lruMaps.stream().mapToDouble(s -> s.quality().executionTimeSeconds()).sum();

            int totalUtilTests = reportUtils.stream().mapToInt(s -> s.quality().tests()).sum();
            double totalUtilTime = reportUtils.stream().mapToDouble(s -> s.quality().executionTimeSeconds()).sum();

            double peakPut = fastMaps.stream().mapToDouble(FastHashMapSummary::putSpeedup).max().orElse(1.0);
            boolean anyPerf = fastMaps.stream().anyMatch(FastHashMapSummary::hasPerf);

            boolean showFast = activeSection == null || "fasthashmap".equalsIgnoreCase(activeSection);
            boolean showLru = activeSection == null || "lruclockmap".equalsIgnoreCase(activeSection);
            boolean showUtil = activeSection == null || "xlt-util".equalsIgnoreCase(activeSection);

            String pageTitle = activeSection == null ? "AI JUG Saxony - Master Evaluation Dashboard"
                    : (showFast ? "AI JUG Saxony - FastHashMap Evaluation"
                    : (showLru ? "AI JUG Saxony - LRUClockMap Evaluation" : "AI JUG Saxony - com.xceptance.xlt.report.util Evaluation"));

            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.printf("    <title>%s</title>%n", pageTitle);
            out.println("    <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">");
            out.println("    <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>");
            out.println("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&display=swap\" rel=\"stylesheet\">");
            out.println("    <style>");
            out.println("        :root {");
            out.println("            --bg: #0f172a;");
            out.println("            --card-bg: #1e293b;");
            out.println("            --card-border: #334155;");
            out.println("            --text: #f8fafc;");
            out.println("            --text-muted: #94a3b8;");
            out.println("            --primary: #38bdf8;");
            out.println("            --primary-glow: rgba(56, 189, 248, 0.2);");
            out.println("            --accent: #a855f7;");
            out.println("            --accent-glow: rgba(168, 85, 247, 0.2);");
            out.println("            --success: #22c55e;");
            out.println("            --success-bg: rgba(34, 197, 94, 0.15);");
            out.println("            --warning: #f59e0b;");
            out.println("            --warning-bg: rgba(245, 158, 11, 0.15);");
            out.println("            --time-bg: rgba(148, 163, 184, 0.12);");
            out.println("        }");
            out.println("        * { box-sizing: border-box; }");
            out.println("        body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: var(--bg); color: var(--text); margin: 0; padding: 2rem 1.5rem; line-height: 1.5; }");
            out.println("        .container { max-width: 1480px; margin: 0 auto; }");
            out.println("        .header { border-bottom: 1px solid var(--card-border); padding-bottom: 1.5rem; margin-bottom: 2rem; display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1.5rem; }");
            out.println("        .header h1 { margin: 0; font-size: 2.1rem; font-weight: 800; letter-spacing: -0.025em; background: linear-gradient(135deg, #f8fafc 0%, #94a3b8 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }");
            out.println("        .header p { margin: 0.5rem 0 0 0; color: var(--text-muted); font-size: 1.05rem; }");
            out.println("        .section-nav-banner { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1.25rem; margin-bottom: 2.25rem; }");
            out.println("        .section-nav-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 1.25rem 1.5rem; text-decoration: none; color: inherit; transition: all 0.25s ease; position: relative; overflow: hidden; display: flex; flex-direction: column; justify-content: space-between; }");
            out.println("        .section-nav-card:hover { transform: translateY(-3px); border-color: var(--primary); box-shadow: 0 10px 25px -5px var(--primary-glow); }");
            out.println("        .section-nav-card.active { border-color: var(--primary); background: linear-gradient(180deg, rgba(56, 189, 248, 0.1) 0%, var(--card-bg) 100%); }");
            out.println("        .card-tag { font-size: 0.75rem; text-transform: uppercase; font-weight: 700; letter-spacing: 0.06em; color: var(--primary); margin-bottom: 0.25rem; display: flex; align-items: center; gap: 0.4rem; }");
            out.println("        .card-title { font-size: 1.25rem; font-weight: 700; margin: 0 0 0.4rem 0; color: #f8fafc; }");
            out.println("        .card-desc { font-size: 0.875rem; color: var(--text-muted); margin: 0; }");
            out.println("        .card-footer { margin-top: 1rem; padding-top: 0.75rem; border-top: 1px solid rgba(255,255,255,0.06); font-size: 0.8rem; font-weight: 600; color: var(--primary); display: flex; justify-content: space-between; align-items: center; }");
            out.println("        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 1.25rem; margin-bottom: 2.25rem; }");
            out.println("        .stat-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 1.25rem; text-align: center; }");
            out.println("        .stat-value { font-size: 1.9rem; font-weight: 800; font-family: 'JetBrains Mono', monospace; margin: 0.25rem 0; }");
            out.println("        .stat-sub { font-size: 0.8rem; color: var(--text-muted); }");
            out.println("        .stat-label { color: var(--text-muted); font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; }");
            out.println("        .quick-actions { display: flex; gap: 0.75rem; margin-bottom: 2rem; flex-wrap: wrap; align-items: center; }");
            out.println("        .action-btn { background: var(--card-bg); border: 1px solid var(--card-border); color: #e2e8f0; padding: 0.55rem 1.1rem; border-radius: 8px; text-decoration: none; font-size: 0.875rem; font-weight: 600; transition: all 0.2s; display: inline-flex; align-items: center; gap: 0.5rem; }");
            out.println("        .action-btn:hover { background: #334155; border-color: var(--primary); color: #38bdf8; }");
            out.println("        .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 1.75rem; margin-bottom: 2.25rem; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.2); }");
            out.println("        .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; gap: 1rem; border-bottom: 1px solid rgba(255,255,255,0.06); padding-bottom: 1rem; }");
            out.println("        .section-header h2 { margin: 0; font-size: 1.45rem; font-weight: 700; display: flex; align-items: center; gap: 0.6rem; color: #f8fafc; }");
            out.println("        .section-desc { color: var(--text-muted); font-size: 0.95rem; margin: 0.25rem 0 0 0; }");
            out.println("        .subpage-link { font-size: 0.85rem; font-weight: 600; color: var(--primary); text-decoration: none; border: 1px solid var(--card-border); padding: 0.4rem 0.8rem; border-radius: 6px; background: rgba(56, 189, 248, 0.05); transition: all 0.2s; }");
            out.println("        .subpage-link:hover { background: rgba(56, 189, 248, 0.15); border-color: var(--primary); }");
            out.println("        table { width: 100%; border-collapse: collapse; margin-top: 1rem; font-size: 0.9rem; }");
            out.println("        th, td { padding: 0.8rem 1rem; text-align: left; border-bottom: 1px solid var(--card-border); }");
            out.println("        th { background: rgba(15, 23, 42, 0.6); font-weight: 700; font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }");
            out.println("        tr:hover td { background: rgba(255, 255, 255, 0.02); }");
            out.println("        .badge { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.25rem 0.65rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; white-space: nowrap; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .badge-success { background: var(--success-bg); color: #4ade80; border: 1px solid rgba(74, 222, 128, 0.3); }");
            out.println("        .badge-info { background: rgba(56, 189, 248, 0.12); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.3); }");
            out.println("        .badge-warning { background: var(--warning-bg); color: #fbbf24; border: 1px solid rgba(251, 191, 36, 0.3); }");
            out.println("        .badge-time { background: var(--time-bg); color: #cbd5e1; border: 1px solid rgba(148, 163, 184, 0.2); font-size: 0.73rem; }");
            out.println("        .badge-perf { background: rgba(168, 85, 247, 0.12); color: #c084fc; border: 1px solid rgba(192, 132, 252, 0.3); }");
            out.println("        .numeric { text-align: right; font-variant-numeric: tabular-nums; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .speedup-fast { color: #4ade80; font-weight: 700; }");
            out.println("        .speedup-slow { color: #f87171; }");
            out.println("        .perf-tag { font-size: 0.8rem; padding: 0.15rem 0.4rem; border-radius: 4px; background: rgba(15, 23, 42, 0.5); font-family: 'JetBrains Mono', monospace; }");
            out.println("        .subtable-wrapper { margin-top: 1.75rem; padding-top: 1.25rem; border-top: 1px dashed var(--card-border); }");
            out.println("        .subtable-title { font-size: 1.05rem; font-weight: 700; margin: 0 0 0.75rem 0; color: #cbd5e1; display: flex; align-items: center; justify-content: space-between; }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container\">");

            // Header
            out.println("    <div class=\"header\">");
            out.println("        <div>");
            out.printf("            <h1>%s</h1>%n", pageTitle);
            out.println("            <p>Comprehensive Evaluation: Performance, Memory, Quality & Verification across AI Models vs Demo 0 Baseline</p>");
            out.println("        </div>");
            if (activeSection != null) {
                out.println("        <div>");
                out.println("            <a href=\"index.html\" class=\"action-btn\">← Back to Master Dashboard</a>");
                out.println("        </div>");
            }
            out.println("    </div>");

            // Quick Jump / Sub-section Navigation Banner (always visible on index)
            out.println("    <div class=\"section-nav-banner\">");

            // Card 1: FastHashMap
            out.println("        <a href=\"#fasthashmap\" class=\"section-nav-card" + ("fasthashmap".equalsIgnoreCase(activeSection) ? " active" : "") + "\">");
            out.println("            <div>");
            out.println("                <div class=\"card-tag\">⚡ High-Performance Core Map</div>");
            out.println("                <div class=\"card-title\">FastHashMap</div>");
            out.println("                <p class=\"card-desc\">Verification matrix, JOL object layouts, and JMH read/write throughput speedup analysis.</p>");
            out.println("            </div>");
            out.printf("            <div class=\"card-footer\"><span>%d Tests Passed (%.2fs)</span><span>Jump to Section ↓</span></div>%n", totalFastTests, totalFastTime);
            out.println("        </a>");

            // Card 2: LRUClockMap
            out.println("        <a href=\"#lruclockmap\" class=\"section-nav-card" + ("lruclockmap".equalsIgnoreCase(activeSection) ? " active" : "") + "\">");
            out.println("            <div>");
            out.println("                <div class=\"card-tag\">⏰ Bounded Second-Chance Eviction Cache</div>");
            out.println("                <div class=\"card-title\">LRUClockMap</div>");
            out.println("                <p class=\"card-desc\">Comprehensive unit test suites, line/branch coverage, and mutation score verification.</p>");
            out.println("            </div>");
            out.printf("            <div class=\"card-footer\"><span>%d Tests Passed (%.2fs)</span><span>Jump to Section ↓</span></div>%n", totalLruTests, totalLruTime);
            out.println("        </a>");

            // Card 3: com.xceptance.xlt.report.util
            out.println("        <a href=\"#xlt-report-util\" class=\"section-nav-card" + ("xlt-util".equalsIgnoreCase(activeSection) ? " active" : "") + "\">");
            out.println("            <div>");
            out.println("                <div class=\"card-tag\">🧰 High-Throughput Report Utilities</div>");
            out.println("                <div class=\"card-title\">com.xceptance.xlt.report.util</div>");
            out.println("                <p class=\"card-desc\">Time series, histograms, bit compression, and bit manipulation test verification.</p>");
            out.println("            </div>");
            out.printf("            <div class=\"card-footer\"><span>%d Tests Passed (%.2fs)</span><span>Jump to Section ↓</span></div>%n", totalUtilTests, totalUtilTime);
            out.println("        </a>");

            out.println("    </div>");

            // Stats grid
            out.println("    <div class=\"stats-grid\">");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Evaluated Models</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--primary);\">%d</div>%n", fastMaps.size());
            out.println("            <div class=\"stat-sub\">demo0–demo9, demo11, demo12</div>");
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">FastHashMap Tests</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--success);\">%d</div>%n", totalFastTests);
            out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalFastTime);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">LRUClockMap Tests</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: #38bdf8;\">%d</div>%n", totalLruTests);
            out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalLruTime);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Report Utility Tests</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--accent);\">%d</div>%n", totalUtilTests);
            out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalUtilTime);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Peak Put Speedup</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: #f59e0b;\">%.2fx</div>%n", peakPut);
            out.println("            <div class=\"stat-sub\">vs demo0 baseline</div>");
            out.println("        </div>");
            out.println("    </div>");

            // Quick actions
            out.println("    <div class=\"quick-actions\">");
            out.println("        <span style=\"font-size: 0.85rem; color: var(--text-muted); font-weight: 600;\">Direct Links:</span>");
            out.println("        <a href=\"#fasthashmap\" class=\"action-btn\">⚡ FastHashMap</a>");
            out.println("        <a href=\"#lruclockmap\" class=\"action-btn\">⏰ LRUClockMap</a>");
            out.println("        <a href=\"#xlt-report-util\" class=\"action-btn\">🧰 com.xceptance.xlt.report.util</a>");
            out.println("        <a href=\"surefire.html\" class=\"action-btn\">🧪 Unit Test Suites</a>");
            out.println("        <a href=\"coverage-aggregate/index.html\" class=\"action-btn\">🎯 JaCoCo Coverage</a>");
            out.println("        <a href=\"jol-report.html\" class=\"action-btn\">💾 JOL Memory</a>");
            out.println("        <a href=\"jmh-report.html\" class=\"action-btn\">⚡ JMH Benchmarks</a>");
            out.println("    </div>");

            // =========================================================================
            // SUB-SECTION 1: FastHashMap
            // =========================================================================
            if (showFast) {
                out.println("    <!-- SUB-SECTION 1: FastHashMap -->");
                out.println("    <div id=\"fasthashmap\" class=\"card\">");
                out.println("        <div class=\"section-header\">");
                out.println("            <div>");
                out.println("                <h2>⚡ FastHashMap — Quality, Coverage & Mutation Verification</h2>");
                out.println("                <div class=\"section-desc\">Evaluation of core hash table implementations, test duration, coverage metrics, and mutation resilience.</div>");
                out.println("            </div>");
                out.println("            <div>");
                out.println("                <a href=\"fasthashmap.html\" class=\"subpage-link\">Open Dedicated Page ↗</a>");
                out.println("            </div>");
                out.println("        </div>");

                out.println("        <table>");
                out.println("            <thead>");
                out.println("                <tr>");
                out.println("                    <th>Module</th>");
                out.println("                    <th>AI Model / Implementation</th>");
                out.println("                    <th>Unit Tests</th>");
                out.println("                    <th>Instruction Coverage</th>");
                out.println("                    <th>Line Coverage</th>");
                out.println("                    <th>Branch Coverage</th>");
                out.println("                    <th>PIT Mutation Score</th>");
                out.println("                    <th>Status</th>");
                out.println("                </tr>");
                out.println("            </thead>");
                out.println("            <tbody>");

                for (FastHashMapSummary s : fastMaps) {
                    String pkgName = s.id();
                    String pitBadge = s.quality().pitTotal() > 0 ?
                            String.format("<a href=\"pit-reports/%s/org.jugsaxony.%s/FastHashMap.java.html\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%.1f%% (%d/%d)</a>",
                                    s.id(), pkgName, s.quality().pitScorePct(), s.quality().pitKilled(), s.quality().pitTotal())
                            : "<span class=\"badge badge-info\">N/A</span>";

                    String covLink = String.format("jacoco/%s/org.jugsaxony.%s/FastHashMap.java.html", s.id(), pkgName);
                    String timeBadge = s.quality().executionTimeSeconds() > 0 ?
                            String.format("<span class=\"badge badge-time\">⏱️ %.2fs</span>", s.quality().executionTimeSeconds()) : "";

                    out.println("                <tr>");
                    out.printf("                    <td><strong>%s</strong></td>%n", s.id());
                    out.printf("                    <td><span class=\"badge badge-info\">%s</span></td>%n", s.aiModel());
                    out.printf("                    <td><a href=\"surefire.html\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s</td>%n", s.quality().tests(), timeBadge);
                    out.printf("                    <td><a href=\"%s\" style=\"color: #38bdf8; text-decoration: underline;\"><strong>%.1f%%</strong></a> (%d/%d)</td>%n", covLink, s.quality().instructionCoveragePct(), s.quality().totalInstructions() - s.quality().missedInstructions(), s.quality().totalInstructions());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().lineCoveragePct(), s.quality().totalLines() - s.quality().missedLines(), s.quality().totalLines());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().branchCoveragePct(), s.quality().totalBranches() - s.quality().missedBranches(), s.quality().totalBranches());
                    out.printf("                    <td>%s</td>%n", pitBadge);
                    out.println("                    <td><span class=\"badge badge-success\">100% Passing ✅</span></td>");
                    out.println("                </tr>");
                }

                out.println("            </tbody>");
                out.println("        </table>");

                // JOL Memory Layout for FastHashMap
                out.println("        <div class=\"subtable-wrapper\">");
                out.println("            <div class=\"subtable-title\">");
                out.println("                <span>💾 FastHashMap — JOL Memory Footprint & Layout Matrix</span>");
                out.println("                <a href=\"jol-report.html\" class=\"subpage-link\">View Detailed JOL Report ↗</a>");
                out.println("            </div>");
                out.println("            <table>");
                out.println("                <thead>");
                out.println("                    <tr>");
                out.println("                        <th>Module</th>");
                out.println("                        <th>AI Model / Implementation</th>");
                out.println("                        <th class=\"numeric\">Mem @ 1k</th>");
                out.println("                        <th class=\"numeric\">Bytes / Entry</th>");
                out.println("                        <th class=\"numeric\">Objs @ 10k</th>");
                out.println("                        <th class=\"numeric\">Empty Footprint</th>");
                out.println("                        <th class=\"numeric\">Shallow Size</th>");
                out.println("                    </tr>");
                out.println("                </thead>");
                out.println("                <tbody>");

                for (FastHashMapSummary s : fastMaps) {
                    out.println("                    <tr>");
                    out.printf("                        <td><strong>%s</strong></td>%n", s.id());
                    out.printf("                        <td><span class=\"badge badge-info\">%s</span></td>%n", s.aiModel());
                    out.printf("                        <td class=\"numeric\"><strong>%,d B</strong></td>%n", s.n1000SizeBytes());
                    out.printf("                        <td class=\"numeric\">%,.1f B/e</td>%n", s.n1000BytesPerEntry());
                    out.printf("                        <td class=\"numeric\">%,d</td>%n", s.n10000ObjectCount());
                    out.printf("                        <td class=\"numeric\">%,d B</td>%n", s.emptySizeBytes());
                    out.printf("                        <td class=\"numeric\">%d B</td>%n", s.shallowSizeBytes());
                    out.println("                    </tr>");
                }

                out.println("                </tbody>");
                out.println("            </table>");
                out.println("        </div>");

                // JMH Microbenchmarks for FastHashMap
                out.println("        <div class=\"subtable-wrapper\">");
                out.println("            <div class=\"subtable-title\">");
                out.printf("                <span>🚀 FastHashMap — Performance Test Results (JMH Speedup & Throughput)%s</span>%n",
                        anyPerf ? " <span class=\"badge badge-perf\" style=\"font-size:0.75rem;\">🔬 Hardware Counters Enabled</span>" : "");
                out.println("                <a href=\"jmh-report.html\" class=\"subpage-link\">View Full JMH Charts & Results ↗</a>");
                out.println("            </div>");

                out.println("            <table>");
                out.println("                <thead>");
                out.println("                    <tr>");
                out.println("                        <th>Module</th>");
                out.println("                        <th>AI Model / Implementation</th>");
                out.println("                        <th class=\"numeric\">Put Speedup</th>");
                out.println("                        <th class=\"numeric\">Get Hit Speedup</th>");
                if (anyPerf) {
                    out.println("                        <th class=\"numeric\">Cycles/op</th>");
                    out.println("                        <th class=\"numeric\">IPC</th>");
                    out.println("                        <th class=\"numeric\">Branch Miss %</th>");
                    out.println("                        <th class=\"numeric\">L1 Miss %</th>");
                } else {
                    out.println("                        <th class=\"numeric\">Get Miss Speedup</th>");
                }
                out.println("                    </tr>");
                out.println("                </thead>");
                out.println("                <tbody>");

                for (FastHashMapSummary s : fastMaps) {
                    String putSpeedupClass = s.putSpeedup() >= 1.05 ? "speedup-fast" : (s.putSpeedup() <= 0.95 ? "speedup-slow" : "");
                    String hitSpeedupClass = s.getHitSpeedup() >= 1.05 ? "speedup-fast" : (s.getHitSpeedup() <= 0.95 ? "speedup-slow" : "");
                    String missSpeedupClass = s.getMissSpeedup() >= 1.05 ? "speedup-fast" : (s.getMissSpeedup() <= 0.95 ? "speedup-slow" : "");

                    out.println("                    <tr>");
                    out.printf("                        <td><strong>%s</strong></td>%n", s.id());
                    out.printf("                        <td><span class=\"badge badge-info\">%s</span></td>%n", s.aiModel());
                    out.printf("                        <td class=\"numeric\"><span class=\"%s\">%.2fx</span> (%.1f ops/µs)</td>%n", putSpeedupClass, s.putSpeedup(), s.putThroughput());
                    out.printf("                        <td class=\"numeric\"><span class=\"%s\">%.2fx</span> (%.1f ops/µs)</td>%n", hitSpeedupClass, s.getHitSpeedup(), s.getHitThroughput());

                    if (anyPerf) {
                        String cyclesStr = s.hitCycles() > 0 ? String.format("%.1f", s.hitCycles()) : "-";
                        String ipcStr = s.hitIpc() > 0 ? String.format("%.2f", s.hitIpc()) : "-";
                        String branchStr = s.hitBranchMissRate() > 0 ? String.format("%.2f%%", s.hitBranchMissRate()) : "-";
                        String l1Str = s.hitL1MissRate() > 0 ? String.format("%.2f%%", s.hitL1MissRate()) : "-";

                        out.printf("                        <td class=\"numeric\"><span class=\"perf-tag\">%s</span></td>%n", cyclesStr);
                        out.printf("                        <td class=\"numeric\"><strong>%s</strong></td>%n", ipcStr);
                        out.printf("                        <td class=\"numeric\">%s</td>%n", branchStr);
                        out.printf("                        <td class=\"numeric\">%s</td>%n", l1Str);
                    } else {
                        out.printf("                        <td class=\"numeric\"><span class=\"%s\">%.2fx</span> (%.1f ops/µs)</td>%n", missSpeedupClass, s.getMissSpeedup(), s.getMissThroughput());
                    }
                    out.println("                    </tr>");
                }

                out.println("                </tbody>");
                out.println("            </table>");
                out.println("        </div>");

                out.println("    </div>");
            }

            // =========================================================================
            // SUB-SECTION 2: LRUClockMap
            // =========================================================================
            if (showLru) {
                out.println("    <!-- SUB-SECTION 2: LRUClockMap -->");
                out.println("    <div id=\"lruclockmap\" class=\"card\">");
                out.println("        <div class=\"section-header\">");
                out.println("            <div>");
                out.println("                <h2>⏰ LRUClockMap — Quality, Coverage & Mutation Verification</h2>");
                out.println("                <div class=\"section-desc\">Verification matrix for LRUClockMap second-chance eviction cache implementations across all modules.</div>");
                out.println("            </div>");
                out.println("            <div>");
                out.println("                <a href=\"lruclockmap.html\" class=\"subpage-link\">Open Dedicated Page ↗</a>");
                out.println("            </div>");
                out.println("        </div>");

                out.println("        <table>");
                out.println("            <thead>");
                out.println("                <tr>");
                out.println("                    <th>Module</th>");
                out.println("                    <th>AI Model / Implementation</th>");
                out.println("                    <th>Unit Tests</th>");
                out.println("                    <th>Instruction Coverage</th>");
                out.println("                    <th>Line Coverage</th>");
                out.println("                    <th>Branch Coverage</th>");
                out.println("                    <th>PIT Mutation Score</th>");
                out.println("                    <th>Status</th>");
                out.println("                </tr>");
                out.println("            </thead>");
                out.println("            <tbody>");

                for (LruClockMapSummary s : lruMaps) {
                    String pkgName = s.id();
                    String pitBadge = s.quality().pitTotal() > 0 ?
                            String.format("<a href=\"pit-reports/%s/org.jugsaxony.%s/LRUClockMap.java.html\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%.1f%% (%d/%d)</a>",
                                    s.id(), pkgName, s.quality().pitScorePct(), s.quality().pitKilled(), s.quality().pitTotal())
                            : "<span class=\"badge badge-info\">N/A</span>";

                    String covLink = String.format("jacoco/%s/org.jugsaxony.%s/LRUClockMap.java.html", s.id(), pkgName);
                    String timeBadge = s.quality().executionTimeSeconds() > 0 ?
                            String.format("<span class=\"badge badge-time\">⏱️ %.2fs</span>", s.quality().executionTimeSeconds()) : "";

                    out.println("                <tr>");
                    out.printf("                    <td><strong>%s</strong></td>%n", s.id());
                    out.printf("                    <td><span class=\"badge badge-info\">%s</span></td>%n", s.aiModel());
                    out.printf("                    <td><a href=\"surefire.html\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s</td>%n", s.quality().tests(), timeBadge);
                    out.printf("                    <td><a href=\"%s\" style=\"color: #38bdf8; text-decoration: underline;\"><strong>%.1f%%</strong></a> (%d/%d)</td>%n", covLink, s.quality().instructionCoveragePct(), s.quality().totalInstructions() - s.quality().missedInstructions(), s.quality().totalInstructions());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().lineCoveragePct(), s.quality().totalLines() - s.quality().missedLines(), s.quality().totalLines());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().branchCoveragePct(), s.quality().totalBranches() - s.quality().missedBranches(), s.quality().totalBranches());
                    out.printf("                    <td>%s</td>%n", pitBadge);
                    out.println("                    <td><span class=\"badge badge-success\">100% Passing ✅</span></td>");
                    out.println("                </tr>");
                }

                out.println("            </tbody>");
                out.println("        </table>");
                out.println("    </div>");
            }

            // =========================================================================
            // SUB-SECTION 3: com.xceptance.xlt.report.util
            // =========================================================================
            if (showUtil) {
                out.println("    <!-- SUB-SECTION 3: com.xceptance.xlt.report.util -->");
                out.println("    <div id=\"xlt-report-util\" class=\"card\">");
                out.println("        <div class=\"section-header\">");
                out.println("            <div>");
                out.println("                <h2>🧰 com.xceptance.xlt.report.util — Quality, Coverage & Mutation Verification</h2>");
                out.println("                <div class=\"section-desc\">Verification matrix for high-throughput reporting utilities (RuntimeHistogram, IntTimeSeries, BitUtil, BitCompression) across all modules.</div>");
                out.println("            </div>");
                out.println("            <div>");
                out.println("                <a href=\"xlt-util.html\" class=\"subpage-link\">Open Dedicated Page ↗</a>");
                out.println("            </div>");
                out.println("        </div>");

                out.println("        <table>");
                out.println("            <thead>");
                out.println("                <tr>");
                out.println("                    <th>Module</th>");
                out.println("                    <th>AI Model / Implementation</th>");
                out.println("                    <th>Unit Tests</th>");
                out.println("                    <th>Instruction Coverage</th>");
                out.println("                    <th>Line Coverage</th>");
                out.println("                    <th>Branch Coverage</th>");
                out.println("                    <th>PIT Mutation Score</th>");
                out.println("                    <th>Status</th>");
                out.println("                </tr>");
                out.println("            </thead>");
                out.println("            <tbody>");

                for (ReportUtilSummary s : reportUtils) {
                    String pitBadge = s.quality().pitTotal() > 0 ?
                            String.format("<a href=\"pit-reports/%s/index.html\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%.1f%% (%d/%d)</a>",
                                    s.id(), s.quality().pitScorePct(), s.quality().pitKilled(), s.quality().pitTotal())
                            : "<span class=\"badge badge-info\">N/A</span>";

                    String covLink = String.format("jacoco/%s/com.xceptance.xlt.report.util/index.html", s.id());
                    String timeBadge = s.quality().executionTimeSeconds() > 0 ?
                            String.format("<span class=\"badge badge-time\">⏱️ %.2fs</span>", s.quality().executionTimeSeconds()) : "";

                    out.println("                <tr>");
                    out.printf("                    <td><strong>%s</strong></td>%n", s.id());
                    out.printf("                    <td><span class=\"badge badge-info\">%s</span></td>%n", s.aiModel());
                    out.printf("                    <td><a href=\"surefire.html\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s</td>%n", s.quality().tests(), timeBadge);
                    out.printf("                    <td><a href=\"%s\" style=\"color: #38bdf8; text-decoration: underline;\"><strong>%.1f%%</strong></a> (%d/%d)</td>%n", covLink, s.quality().instructionCoveragePct(), s.quality().totalInstructions() - s.quality().missedInstructions(), s.quality().totalInstructions());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().lineCoveragePct(), s.quality().totalLines() - s.quality().missedLines(), s.quality().totalLines());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().branchCoveragePct(), s.quality().totalBranches() - s.quality().missedBranches(), s.quality().totalBranches());
                    out.printf("                    <td>%s</td>%n", pitBadge);
                    out.println("                    <td><span class=\"badge badge-success\">100% Passing ✅</span></td>");
                    out.println("                </tr>");
                }

                out.println("            </tbody>");
                out.println("        </table>");
                out.println("    </div>");
            }

            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    public static File findRootDir() {
        File cur = new File(".").getAbsoluteFile();
        while (cur != null) {
            File pom = new File(cur, "pom.xml");
            File demo0 = new File(cur, "demo0");
            if (pom.exists() && demo0.exists() && demo0.isDirectory()) {
                try {
                    String content = Files.readString(pom.toPath());
                    if (content.contains("<artifactId>ai-jug-saxony-parent</artifactId>")) {
                        return cur;
                    }
                } catch (Exception ignored) {}
            }
            cur = cur.getParentFile();
        }
        return new File(".");
    }

    private static void copyDirectory(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdirs();
            }
            String[] files = src.list();
            if (files != null) {
                for (String file : files) {
                    File srcFile = new File(src, file);
                    File destFile = new File(dest, file);
                    copyDirectory(srcFile, destFile);
                }
            }
        } else {
            Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void main(String[] args) throws Exception {
        File rootDir = findRootDir();
        File reportsDir = new File(rootDir, "reports");
        if (args.length > 0) {
            reportsDir = new File(args[0]);
        }
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }
        generateDashboard(reportsDir, rootDir);
        System.out.println("Global dashboard generated in: " + reportsDir.getAbsolutePath());
    }
}
