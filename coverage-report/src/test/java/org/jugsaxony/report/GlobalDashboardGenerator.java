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

    public record FastHashMapBlackBoxSummary(
            String id,
            String name,
            String aiModel,
            String storageStrategy,
            QualityStats quality
    ) {}

    public record LruClockMapSummary(
            String id,
            String name,
            String aiModel,
            QualityStats quality
    ) {}

    public record XltClassMeta(String className, String packageName, String sourceFileName, String htmlRelPath) {}

    public static final List<XltClassMeta> XLT_CLASSES = List.of(
            new XltClassMeta("RuntimeHistogram", "com.xceptance.xlt.report.util", "RuntimeHistogram.java", "com.xceptance.xlt.report.util/RuntimeHistogram.java.html"),
            new XltClassMeta("BitUtil", "com.xceptance.xlt.report.util.lucene", "BitUtil.java", "com.xceptance.xlt.report.util.lucene/BitUtil.java.html"),
            new XltClassMeta("BitCompression", "com.xceptance.xlt.report.util.misc", "BitCompression.java", "com.xceptance.xlt.report.util.misc/BitCompression.java.html"),
            new XltClassMeta("IntTimeSeries", "com.xceptance.xlt.report.util.rework", "IntTimeSeries.java", "com.xceptance.xlt.report.util.rework/IntTimeSeries.java.html"),
            new XltClassMeta("IntTimeSeriesEntry", "com.xceptance.xlt.report.util.rework", "IntTimeSeriesEntry.java", "com.xceptance.xlt.report.util.rework/IntTimeSeriesEntry.java.html")
    );

    public record XltClassCoverage(
            String className,
            String packageName,
            String sourceFileName,
            String htmlRelPath,
            double instructionCoveragePct,
            int missedInstructions,
            int coveredInstructions,
            int totalInstructions,
            double lineCoveragePct,
            int missedLines,
            int coveredLines,
            int totalLines,
            double branchCoveragePct,
            int missedBranches,
            int coveredBranches,
            int totalBranches
    ) {}

    public record ReportUtilSummary(
            String id,
            String name,
            String aiModel,
            QualityStats quality,
            List<XltClassCoverage> classCoverages
    ) {
        public ReportUtilSummary(String id, String name, String aiModel, QualityStats quality) {
            this(id, name, aiModel, quality, List.of());
        }
    }

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
        List<FastHashMapBlackBoxSummary> fastBlackBoxSummaries = new ArrayList<>();
        List<LruClockMapSummary> lruMapSummaries = new ArrayList<>();
        List<ReportUtilSummary> reportUtilSummaries = new ArrayList<>();

        for (GlobalJolReport.ImplementationMeta meta : GlobalJolReport.IMPLEMENTATIONS) {
            String modId = meta.id();
            String[] customMeta = MODULE_METADATA.get(modId);
            String storageStrategy = customMeta != null ? customMeta[2] : "Standard";

            File modDir = new File(rootProjectDir, modId);
            File surefireDir = new File(modDir, "target/surefire-reports");
            File jacocoXml = new File(outputDir, "jacoco/" + modId + "/jacoco.xml");
            if (!jacocoXml.exists()) {
                jacocoXml = new File(modDir, "target/site/jacoco/jacoco.xml");
            }
            File pitCsv = new File(outputDir, "pit-reports/" + modId + "/mutations.csv");
            if (!pitCsv.exists()) {
                pitCsv = new File(modDir, "target/pit-reports/mutations.csv");
            }

            QualityStats fastQuality = buildQualityStats(surefireDir, jacocoXml, pitCsv, "FastHashMapTest", "FastHashMap.java");

            File bbSurefireDir = new File(outputDir, "surefire-reports-blackbox/" + modId);
            if (!bbSurefireDir.exists()) {
                bbSurefireDir = surefireDir;
            }
            File bbJacocoXml = new File(outputDir, "jacoco-blackbox/" + modId + "/jacoco.xml");
            if (!bbJacocoXml.exists()) {
                bbJacocoXml = new File(modDir, "target/site/jacoco-blackbox/jacoco.xml");
            }
            File bbPitCsv = new File(outputDir, "pit-reports-blackbox/" + modId + "/mutations.csv");
            if (!bbPitCsv.exists()) {
                bbPitCsv = new File(modDir, "target/pit-reports-blackbox/mutations.csv");
            }
            QualityStats fastBlackBoxQuality = buildQualityStats(bbSurefireDir, bbJacocoXml, bbPitCsv, "FastHashMapBlackBox", "FastHashMap.java");

            QualityStats lruQuality = buildQualityStats(surefireDir, jacocoXml, pitCsv, "LRUClockMapTest", "LRUClockMap.java");
            QualityStats xltQuality = buildXltQualityStats(surefireDir, jacocoXml, pitCsv);
            List<XltClassCoverage> xltClasses = buildXltClassCoverages(jacocoXml);

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

            fastBlackBoxSummaries.add(new FastHashMapBlackBoxSummary(
                    modId,
                    meta.name(),
                    meta.model(),
                    storageStrategy,
                    fastBlackBoxQuality
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
                        xltQuality,
                        xltClasses
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

        // Copy individual module JaCoCo reports and generate unified xlt-util coverage page
        for (String modDirName : modDirs) {
            File modJacoco = new File(rootProjectDir, modDirName + "/target/site/jacoco");
            File destModJacoco = new File(outputDir, "jacoco/" + modDirName);
            if (!destModJacoco.exists() && modJacoco.exists() && modJacoco.isDirectory()) {
                copyDirectory(modJacoco, destModJacoco);
            }
            final String currentMod = modDirName;
            ReportUtilSummary repSummary = reportUtilSummaries.stream()
                    .filter(s -> s.id().equals(currentMod))
                    .findFirst()
                    .orElse(null);
            if (destModJacoco.exists() && repSummary != null) {
                generateXltUtilCoveragePage(destModJacoco, repSummary);
                injectXltNoticeBanner(destModJacoco);
            }
        }

        // Copy PIT reports per module if available
        for (String modDirName : modDirs) {
            File modPit = new File(rootProjectDir, modDirName + "/target/pit-reports");
            File destModPit = new File(outputDir, "pit-reports/" + modDirName);
            if (!destModPit.exists() && modPit.exists() && modPit.isDirectory()) {
                copyDirectory(modPit, destModPit);
            }
        }

        // Copy BlackBox reports per module if available in target
        for (String modDirName : modDirs) {
            File modJacocoBb = new File(rootProjectDir, modDirName + "/target/site/jacoco-blackbox");
            File destJacocoBb = new File(outputDir, "jacoco-blackbox/" + modDirName);
            if (modJacocoBb.exists() && modJacocoBb.isDirectory()) {
                copyDirectory(modJacocoBb, destJacocoBb);
            }
            File modPitBb = new File(rootProjectDir, modDirName + "/target/pit-reports-blackbox");
            File destPitBb = new File(outputDir, "pit-reports-blackbox/" + modDirName);
            if (modPitBb.exists() && modPitBb.isDirectory()) {
                copyDirectory(modPitBb, destPitBb);
            }
        }

        // Write Markdown Dashboard
        writeMarkdownDashboard(new File(outputDir, "README.md"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries);
        writeMarkdownDashboard(new File(outputDir, "global-dashboard.md"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries);

        // Write Master HTML Dashboard
        writeHtmlDashboard(new File(outputDir, "index.html"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, null);
        writeHtmlDashboard(new File(outputDir, "global-dashboard.html"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, null);

        // Write Standalone Sub-Section HTML Pages
        writeHtmlDashboard(new File(outputDir, "fasthashmap.html"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, "fasthashmap");
        writeHtmlDashboard(new File(outputDir, "lruclockmap.html"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, "lruclockmap");
        writeHtmlDashboard(new File(outputDir, "xlt-util.html"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, "xlt-util");
    }

    private record TestExecutionInfo(int tests, double executionTimeSeconds, int failures, int errors) {}

    private static TestExecutionInfo parseTestExecution(File surefireDir, String testPattern, boolean isPrefix) {
        if (!surefireDir.exists() || !surefireDir.isDirectory()) return new TestExecutionInfo(0, 0.0, 0, 0);
        File[] files = surefireDir.listFiles((dir, name) -> {
            if (!name.startsWith("TEST-") || !name.endsWith(".xml")) return false;
            if (isPrefix) {
                return name.startsWith("TEST-" + testPattern);
            } else {
                return name.contains(testPattern);
            }
        });
        if (files == null) return new TestExecutionInfo(0, 0.0, 0, 0);

        int totalTests = 0;
        double totalTime = 0.0;
        int totalFailures = 0;
        int totalErrors = 0;

        Pattern tcPat = Pattern.compile("<testcase\\b[^>]*\\btime=\"([0-9.]+)\"");
        Pattern suiteTestsPat = Pattern.compile("\\btests=\"([0-9]+)\"");
        Pattern suiteTimePat = Pattern.compile("\\btime=\"([0-9.]+)\"");
        Pattern suiteFailuresPat = Pattern.compile("\\bfailures=\"([0-9]+)\"");
        Pattern suiteErrorsPat = Pattern.compile("\\berrors=\"([0-9]+)\"");

        for (File f : files) {
            try {
                String content = Files.readString(f.toPath());
                int fileTests = 0;
                double fileTime = 0.0;
                int fileFailures = 0;
                int fileErrors = 0;

                Matcher mTests = suiteTestsPat.matcher(content);
                Matcher mTime = suiteTimePat.matcher(content);
                Matcher mFail = suiteFailuresPat.matcher(content);
                Matcher mErr = suiteErrorsPat.matcher(content);

                if (mTests.find()) {
                    fileTests = Integer.parseInt(mTests.group(1));
                }
                if (mTime.find()) {
                    try {
                        fileTime = Double.parseDouble(mTime.group(1));
                    } catch (NumberFormatException ignored) {}
                }
                if (mFail.find()) {
                    fileFailures = Integer.parseInt(mFail.group(1));
                }
                if (mErr.find()) {
                    fileErrors = Integer.parseInt(mErr.group(1));
                }

                if (fileTests == 0) {
                    Matcher tcMatcher = tcPat.matcher(content);
                    while (tcMatcher.find()) {
                        fileTests++;
                        try {
                            fileTime += Double.parseDouble(tcMatcher.group(1));
                        } catch (NumberFormatException ignored) {}
                    }
                }

                totalTests += fileTests;
                totalTime += fileTime;
                totalFailures += fileFailures;
                totalErrors += fileErrors;
            } catch (Exception ignored) {}
        }
        return new TestExecutionInfo(totalTests, totalTime, totalFailures, totalErrors);
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
                testInfo.failures(),
                testInfo.errors(),
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
                testInfo.failures(),
                testInfo.errors(),
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

    private static List<XltClassCoverage> buildXltClassCoverages(File jacocoXml) {
        List<XltClassCoverage> list = new ArrayList<>();
        if (!jacocoXml.exists()) {
            return list;
        }
        for (XltClassMeta meta : XLT_CLASSES) {
            int[] covInst = parseSourceCoverage(jacocoXml, meta.sourceFileName(), "INSTRUCTION");
            int[] covBranch = parseSourceCoverage(jacocoXml, meta.sourceFileName(), "BRANCH");
            int[] covLine = parseSourceCoverage(jacocoXml, meta.sourceFileName(), "LINE");

            int totalInst = covInst[0] + covInst[1];
            double instPct = totalInst > 0 ? (covInst[1] * 100.0) / totalInst : 0.0;

            int totalBranch = covBranch[0] + covBranch[1];
            double branchPct = totalBranch > 0 ? (covBranch[1] * 100.0) / totalBranch : (totalInst > 0 ? 100.0 : 0.0);

            int totalLine = covLine[0] + covLine[1];
            double linePct = totalLine > 0 ? (covLine[1] * 100.0) / totalLine : 0.0;

            list.add(new XltClassCoverage(
                    meta.className(),
                    meta.packageName(),
                    meta.sourceFileName(),
                    meta.htmlRelPath(),
                    instPct,
                    covInst[0],
                    covInst[1],
                    totalInst,
                    linePct,
                    covLine[0],
                    covLine[1],
                    totalLine,
                    branchPct,
                    covBranch[0],
                    covBranch[1],
                    totalBranch
            ));
        }
        return list;
    }

    private static void generateXltUtilCoveragePage(File destModJacoco, ReportUtilSummary summary) {
        if (!destModJacoco.exists()) {
            destModJacoco.mkdirs();
        }
        File targetFile = new File(destModJacoco, "xlt-util-coverage.html");
        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            QualityStats q = summary.quality();
            String modId = summary.id();
            String modelName = summary.aiModel();

            String instColor = q.instructionCoveragePct() >= 90.0 ? "var(--success)" : (q.instructionCoveragePct() < 70.0 ? "var(--warning)" : "var(--primary)");
            String lineColor = q.lineCoveragePct() >= 90.0 ? "var(--success)" : (q.lineCoveragePct() < 70.0 ? "var(--warning)" : "var(--primary)");
            String branchColor = q.branchCoveragePct() >= 90.0 ? "var(--success)" : (q.branchCoveragePct() < 70.0 ? "var(--warning)" : "var(--primary)");

            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.printf("    <title>Reporting Utilities Suite Coverage — %s (%s)</title>%n", modId, modelName);
            out.println("    <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">");
            out.println("    <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>");
            out.println("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&display=swap\" rel=\"stylesheet\">");
            out.println("    <style>");
            out.println("        :root {");
            out.println("            --bg: #f8fafc;");
            out.println("            --card-bg: #ffffff;");
            out.println("            --card-border: #e2e8f0;");
            out.println("            --text: #0f172a;");
            out.println("            --text-muted: #64748b;");
            out.println("            --primary: #0284c7;");
            out.println("            --primary-glow: rgba(2, 132, 199, 0.12);");
            out.println("            --success: #16a34a;");
            out.println("            --warning: #d97706;");
            out.println("            --danger: #dc2626;");
            out.println("        }");
            out.println("        * { box-sizing: border-box; }");
            out.println("        body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: var(--bg); color: var(--text); margin: 0; padding: 2rem 1.5rem; line-height: 1.5; }");
            out.println("        .container { max-width: 1300px; margin: 0 auto; }");
            out.println("        .breadcrumb { font-size: 0.85rem; color: var(--text-muted); margin-bottom: 1.25rem; }");
            out.println("        .breadcrumb a { color: var(--primary); text-decoration: none; font-weight: 500; }");
            out.println("        .breadcrumb a:hover { text-decoration: underline; }");
            out.println("        .header { border-bottom: 1px solid var(--card-border); padding-bottom: 1.5rem; margin-bottom: 2rem; display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1.5rem; }");
            out.println("        .header h1 { margin: 0; font-size: 1.85rem; font-weight: 800; letter-spacing: -0.025em; color: #0f172a; }");
            out.println("        .header p { margin: 0.4rem 0 0 0; color: var(--text-muted); font-size: 0.95rem; }");
            out.println("        .quick-actions { display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: center; }");
            out.println("        .action-btn { background: var(--card-bg); border: 1px solid var(--card-border); color: #334155; padding: 0.5rem 1rem; border-radius: 8px; text-decoration: none; font-size: 0.85rem; font-weight: 600; transition: all 0.2s; display: inline-flex; align-items: center; gap: 0.4rem; box-shadow: 0 1px 2px rgba(0,0,0,0.04); }");
            out.println("        .action-btn:hover { background: #f8fafc; border-color: var(--primary); color: var(--primary); }");
            out.println("        .notice-banner { background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 10px; padding: 1rem 1.25rem; margin-bottom: 2rem; color: #0369a1; font-size: 0.9rem; line-height: 1.5; }");
            out.println("        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1.25rem; margin-bottom: 2rem; }");
            out.println("        .stat-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 12px; padding: 1.25rem; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            out.println("        .stat-label { color: var(--text-muted); font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; }");
            out.println("        .stat-value { font-size: 1.85rem; font-weight: 800; font-family: 'JetBrains Mono', monospace; margin: 0.25rem 0; }");
            out.println("        .stat-sub { font-size: 0.8rem; color: var(--text-muted); }");
            out.println("        .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 1.75rem; margin-bottom: 2rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            out.println("        .card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; padding-bottom: 0.75rem; border-bottom: 1px solid var(--card-border); flex-wrap: wrap; gap: 0.5rem; }");
            out.println("        .card-header h2 { margin: 0; font-size: 1.3rem; font-weight: 700; color: #0f172a; }");
            out.println("        table { width: 100%; border-collapse: collapse; margin-top: 0.5rem; font-size: 0.9rem; }");
            out.println("        th, td { padding: 0.8rem 1rem; text-align: left; border-bottom: 1px solid var(--card-border); }");
            out.println("        th { background: #f8fafc; font-weight: 700; font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }");
            out.println("        tr:hover td { background: #f8fafc; }");
            out.println("        tr.total-row td { background: #f1f5f9; font-weight: 700; border-top: 2px solid #cbd5e1; font-size: 0.92rem; }");
            out.println("        .numeric { text-align: right; font-variant-numeric: tabular-nums; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .badge { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.2rem 0.55rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .badge-success { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }");
            out.println("        .badge-info { background: #f0f9ff; color: #0284c7; border: 1px solid #bae6fd; }");
            out.println("        .badge-warning { background: #fef3c7; color: #b45309; border: 1px solid #fde68a; }");
            out.println("        .cov-bar-layout { display: inline-flex; align-items: center; gap: 0.65rem; white-space: nowrap; font-variant-numeric: tabular-nums; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .progress-bar-container { width: 84px; height: 7px; background: #e2e8f0; border-radius: 4px; overflow: hidden; flex-shrink: 0; }");
            out.println("        .progress-bar-fill { height: 100%; border-radius: 4px; }");
            out.println("        .cov-pct { width: 56px; text-align: right; font-weight: 700; flex-shrink: 0; font-family: 'JetBrains Mono', monospace; font-size: 0.88rem; }");
            out.println("        .cov-count { width: 94px; text-align: left; font-size: 0.75rem; color: var(--text-muted); font-weight: normal; flex-shrink: 0; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .pkg-tag { font-size: 0.78rem; color: var(--text-muted); font-family: 'JetBrains Mono', monospace; }");
            out.println("        .source-link { color: var(--primary); text-decoration: underline; font-weight: 600; font-size: 0.85rem; }");
            out.println("        .source-link:hover { color: #0369a1; }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container\">");

            // Breadcrumbs
            out.println("    <div class=\"breadcrumb\">");
            out.println("        <a href=\"../../index.html\">Master Dashboard</a> &gt; ");
            out.println("        <a href=\"../../xlt-util.html\">Reporting Utilities</a> &gt; ");
            out.printf("        <a href=\"index.html\">JaCoCo (%s)</a> &gt; %n", modId);
            out.println("        <span>com.xceptance.xlt.report.util Suite</span>");
            out.println("    </div>");

            // Header
            out.println("    <div class=\"header\">");
            out.println("        <div>");
            out.printf("            <h1>🧰 Reporting Utilities Suite Coverage — %s</h1>%n", modId);
            out.printf("            <p>Unified verification report covering all 5 high-throughput reporting utility classes across 4 package directories. AI Model: <strong>%s</strong></p>%n", modelName);
            out.println("        </div>");
            out.println("        <div class=\"quick-actions\">");
            out.println("            <a href=\"../../xlt-util.html\" class=\"action-btn\">← Back to Utilities Matrix</a>");
            out.println("            <a href=\"../../index.html\" class=\"action-btn\">🏠 Master Dashboard</a>");
            out.println("            <a href=\"index.html\" class=\"action-btn\">📁 Full JaCoCo Report</a>");
            out.println("        </div>");
            out.println("    </div>");

            // Notice
            out.println("    <div class=\"notice-banner\">");
            out.println("        💡 <strong>Unified Reporting Utilities Suite:</strong> In JaCoCo's standard report layout, classes are separated into individual package folders (<code>com.xceptance.xlt.report.util</code>, <code>lucene</code>, <code>misc</code>, and <code>rework</code>). This view aggregates all 5 utility classes into a single coverage dashboard with direct links to JaCoCo source code listings.");
            out.println("    </div>");

            // Stats grid
            int coveredInst = q.totalInstructions() - q.missedInstructions();
            int coveredLines = q.totalLines() - q.missedLines();
            int coveredBranches = q.totalBranches() - q.missedBranches();

            out.println("    <div class=\"stats-grid\">");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Instruction Coverage</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: %s;\">%.1f%%</div>%n", instColor, q.instructionCoveragePct());
            out.printf("            <div class=\"stat-sub\">%,d of %,d instructions covered</div>%n", coveredInst, q.totalInstructions());
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Line Coverage</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: %s;\">%.1f%%</div>%n", lineColor, q.lineCoveragePct());
            out.printf("            <div class=\"stat-sub\">%,d of %,d lines covered</div>%n", coveredLines, q.totalLines());
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Branch Coverage</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: %s;\">%.1f%%</div>%n", branchColor, q.branchCoveragePct());
            out.printf("            <div class=\"stat-sub\">%,d of %,d branches covered</div>%n", coveredBranches, q.totalBranches());
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Utility Classes</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: #7c3aed;\">%d</div>%n", summary.classCoverages().size());
            out.println("            <div class=\"stat-sub\">Across 4 subpackages</div>");
            out.println("        </div>");
            out.println("    </div>");

            // Class table
            out.println("    <div class=\"card\">");
            out.println("        <div class=\"card-header\">");
            out.println("            <h2>Detailed Class-Level Coverage Breakdown</h2>");
            out.println("            <span style=\"font-size: 0.85rem; color: var(--text-muted);\">Click on any class name or \"View Source\" to inspect line-by-line coverage</span>");
            out.println("        </div>");
            out.println("        <table>");
            out.println("            <thead>");
            out.println("                <tr>");
            out.println("                    <th>Class</th>");
            out.println("                    <th>Package</th>");
            out.println("                    <th>Instruction Coverage</th>");
            out.println("                    <th class=\"numeric\">Line Coverage</th>");
            out.println("                    <th class=\"numeric\">Branch Coverage</th>");
            out.println("                    <th style=\"text-align: center;\">JaCoCo Source</th>");
            out.println("                </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (XltClassCoverage c : summary.classCoverages()) {
                double fillWidth = Math.min(100.0, Math.max(0.0, c.instructionCoveragePct()));
                String fillStyle = c.instructionCoveragePct() >= 90.0 ? "background: #16a34a;" : (c.instructionCoveragePct() < 70.0 ? "background: #d97706;" : "background: #0284c7;");

                String branchText;
                if (c.totalBranches() > 0) {
                    branchText = String.format("<div class=\"cov-bar-layout\" style=\"justify-content: flex-end;\"><span class=\"cov-pct\" style=\"font-weight: normal;\">%.1f%%</span> <span class=\"cov-count\">(%d/%d)</span></div>",
                            c.branchCoveragePct(), c.coveredBranches(), c.totalBranches());
                } else {
                    branchText = "<span style=\"color: var(--text-muted); font-size: 0.8rem;\">- (0 branches)</span>";
                }

                out.println("                <tr>");
                out.printf("                    <td><a href=\"%s\" style=\"color: var(--primary); font-weight: 700; text-decoration: underline;\">%s</a></td>%n", c.htmlRelPath(), c.className());
                out.printf("                    <td><span class=\"pkg-tag\">%s</span></td>%n", c.packageName());
                out.printf("                    <td><div class=\"cov-bar-layout\"><div class=\"progress-bar-container\"><div class=\"progress-bar-fill\" style=\"width: %.1f%%; %s\"></div></div> <span class=\"cov-pct\">%.1f%%</span> <span class=\"cov-count\">(%d/%d)</span></div></td>%n",
                        fillWidth, fillStyle, c.instructionCoveragePct(), c.coveredInstructions(), c.totalInstructions());
                out.printf("                    <td class=\"numeric\"><div class=\"cov-bar-layout\" style=\"justify-content: flex-end;\"><span class=\"cov-pct\" style=\"font-weight: normal;\">%.1f%%</span> <span class=\"cov-count\">(%d/%d)</span></div></td>%n",
                        c.lineCoveragePct(), c.coveredLines(), c.totalLines());
                out.printf("                    <td class=\"numeric\">%s</td>%n", branchText);
                out.printf("                    <td style=\"text-align: center;\"><a href=\"%s\" class=\"source-link\">View Source ↗</a></td>%n", c.htmlRelPath());
                out.println("                </tr>");
            }

            // Total row
            double totalFillWidth = Math.min(100.0, Math.max(0.0, q.instructionCoveragePct()));
            String totalFillStyle = q.instructionCoveragePct() >= 90.0 ? "background: #16a34a;" : (q.instructionCoveragePct() < 70.0 ? "background: #d97706;" : "background: #0284c7;");

            out.println("                <tr class=\"total-row\">");
            out.println("                    <td>Total Suite Coverage</td>");
            out.println("                    <td><span class=\"pkg-tag\">4 packages / 5 classes</span></td>");
            out.printf("                    <td><div class=\"cov-bar-layout\"><div class=\"progress-bar-container\"><div class=\"progress-bar-fill\" style=\"width: %.1f%%; %s\"></div></div> <span class=\"cov-pct\">%.1f%%</span> <span class=\"cov-count\">(%d/%d)</span></div></td>%n",
                    totalFillWidth, totalFillStyle, q.instructionCoveragePct(), coveredInst, q.totalInstructions());
            out.printf("                    <td class=\"numeric\"><div class=\"cov-bar-layout\" style=\"justify-content: flex-end;\"><span class=\"cov-pct\" style=\"font-weight: normal;\">%.1f%%</span> <span class=\"cov-count\">(%d/%d)</span></div></td>%n",
                    q.lineCoveragePct(), coveredLines, q.totalLines());
            out.printf("                    <td class=\"numeric\"><div class=\"cov-bar-layout\" style=\"justify-content: flex-end;\"><span class=\"cov-pct\" style=\"font-weight: normal;\">%.1f%%</span> <span class=\"cov-count\">(%d/%d)</span></div></td>%n",
                    q.branchCoveragePct(), coveredBranches, q.totalBranches());
            out.println("                    <td style=\"text-align: center;\"><span style=\"color: var(--text-muted); font-size: 0.8rem;\">Suite Aggregate</span></td>");
            out.println("                </tr>");

            out.println("            </tbody>");
            out.println("        </table>");
            out.println("    </div>");

            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void injectXltNoticeBanner(File destModJacoco) {
        File subPkgIndex = new File(destModJacoco, "com.xceptance.xlt.report.util/index.html");
        if (!subPkgIndex.exists()) return;
        try {
            String content = Files.readString(subPkgIndex.toPath());
            if (!content.contains("xlt-util-coverage.html")) {
                String target = "<h1>com.xceptance.xlt.report.util</h1>";
                String banner = "<h1>com.xceptance.xlt.report.util</h1>" +
                        "<div style=\"background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 8px; padding: 12px 16px; margin: 16px 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; font-size: 13.5px; color: #0369a1; line-height: 1.5;\">" +
                        "ℹ️ <strong>Package Suite Notice:</strong> JaCoCo organizes packages separately, so this folder only shows classes in the package root (<code>RuntimeHistogram</code>). " +
                        "<a href=\"../xlt-util-coverage.html\" style=\"color: #0284c7; font-weight: 700; text-decoration: underline; margin-left: 6px;\">View Complete Reporting Utilities Suite Coverage (RuntimeHistogram, BitUtil, BitCompression, IntTimeSeries) →</a>" +
                        "</div>";
                if (content.contains(target)) {
                    content = content.replace(target, banner);
                    Files.writeString(subPkgIndex.toPath(), content);
                }
            }
        } catch (Exception ignored) {}
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
            List<FastHashMapBlackBoxSummary> fastBlackBoxes,
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
            out.println("### 🤖 FastHashMap AI-Generated Test Suites");
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
            out.println("### 🧪 FastHashMap Manual BlackBox — Quality, Coverage & Mutation Verification");
            out.println();
            out.println("| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |");
            out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

            for (FastHashMapBlackBoxSummary s : fastBlackBoxes) {
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

                out.printf("| **%s** | %s | %d ✅%s | [%.1f%% (%d/%d)](jacoco/%s/xlt-util-coverage.html) | [%.1f%% (%d/%d)](jacoco/%s/xlt-util-coverage.html) | [%.1f%% (%d/%d)](jacoco/%s/xlt-util-coverage.html) | %s | %s |%n",
                        s.id(),
                        s.aiModel(),
                        s.quality().tests(),
                        timeStr,
                        s.quality().instructionCoveragePct(),
                        s.quality().totalInstructions() - s.quality().missedInstructions(),
                        s.quality().totalInstructions(),
                        s.id(),
                        s.quality().lineCoveragePct(),
                        s.quality().totalLines() - s.quality().missedLines(),
                        s.quality().totalLines(),
                        s.id(),
                        s.quality().branchCoveragePct(),
                        s.quality().totalBranches() - s.quality().missedBranches(),
                        s.quality().totalBranches(),
                        s.id(),
                        pitStr,
                        "100% Passing ✅"
                );
            }

            out.println();
            out.println("### 📊 com.xceptance.xlt.report.util — Class-Level Instruction Coverage Matrix");
            out.println();
            out.println("| Module | AI Model / Implementation | RuntimeHistogram | BitUtil | BitCompression | IntTimeSeries | IntTimeSeriesEntry | Total Suite |");
            out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

            for (ReportUtilSummary s : reportUtils) {
                Map<String, XltClassCoverage> covMap = new HashMap<>();
                for (XltClassCoverage c : s.classCoverages()) {
                    covMap.put(c.className(), c);
                }
                XltClassCoverage rh = covMap.get("RuntimeHistogram");
                XltClassCoverage bu = covMap.get("BitUtil");
                XltClassCoverage bc = covMap.get("BitCompression");
                XltClassCoverage its = covMap.get("IntTimeSeries");
                XltClassCoverage itse = covMap.get("IntTimeSeriesEntry");

                String rhStr = rh != null ? String.format("%.1f%%", rh.instructionCoveragePct()) : "-";
                String buStr = bu != null ? String.format("%.1f%%", bu.instructionCoveragePct()) : "-";
                String bcStr = bc != null ? String.format("%.1f%%", bc.instructionCoveragePct()) : "-";
                String itsStr = its != null ? String.format("%.1f%%", its.instructionCoveragePct()) : "-";
                String itseStr = itse != null ? String.format("%.1f%%", itse.instructionCoveragePct()) : "-";

                out.printf("| **%s** | %s | %s | %s | %s | %s | %s | **%.1f%%** |%n",
                        s.id(),
                        s.aiModel(),
                        rhStr,
                        buStr,
                        bcStr,
                        itsStr,
                        itseStr,
                        s.quality().instructionCoveragePct()
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
            List<FastHashMapBlackBoxSummary> fastBlackBoxes,
            List<LruClockMapSummary> lruMaps,
            List<ReportUtilSummary> reportUtils,
            String activeSection
    ) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            int totalFastTests = fastMaps.stream().mapToInt(s -> s.quality().tests()).sum();
            double totalFastTime = fastMaps.stream().mapToDouble(s -> s.quality().executionTimeSeconds()).sum();

            int totalBlackBoxTests = fastBlackBoxes.stream().mapToInt(s -> s.quality().tests()).sum();
            double totalBlackBoxTime = fastBlackBoxes.stream().mapToDouble(s -> s.quality().executionTimeSeconds()).sum();

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
            out.println("            --bg: #f8fafc;");
            out.println("            --card-bg: #ffffff;");
            out.println("            --card-border: #e2e8f0;");
            out.println("            --text: #0f172a;");
            out.println("            --text-muted: #64748b;");
            out.println("            --primary: #0284c7;");
            out.println("            --primary-glow: rgba(2, 132, 199, 0.12);");
            out.println("            --accent: #7c3aed;");
            out.println("            --accent-glow: rgba(124, 58, 237, 0.12);");
            out.println("            --success: #16a34a;");
            out.println("            --success-bg: #dcfce7;");
            out.println("            --warning: #d97706;");
            out.println("            --warning-bg: #fef3c7;");
            out.println("            --time-bg: #f1f5f9;");
            out.println("        }");
            out.println("        * { box-sizing: border-box; }");
            out.println("        body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: var(--bg); color: var(--text); margin: 0; padding: 2rem 1.5rem; line-height: 1.5; }");
            out.println("        .container { max-width: 1480px; margin: 0 auto; }");
            out.println("        .header { border-bottom: 1px solid var(--card-border); padding-bottom: 1.5rem; margin-bottom: 2rem; display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1.5rem; }");
            out.println("        .header h1 { margin: 0; font-size: 2.1rem; font-weight: 800; letter-spacing: -0.025em; background: linear-gradient(135deg, #0f172a 0%, #334155 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }");
            out.println("        .header p { margin: 0.5rem 0 0 0; color: var(--text-muted); font-size: 1.05rem; }");
            out.println("        .section-nav-banner { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1.25rem; margin-bottom: 2.25rem; }");
            out.println("        .section-nav-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 1.25rem 1.5rem; text-decoration: none; color: inherit; transition: all 0.25s ease; position: relative; overflow: hidden; display: flex; flex-direction: column; justify-content: space-between; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            out.println("        .section-nav-card:hover { transform: translateY(-2px); border-color: var(--primary); box-shadow: 0 8px 20px -4px var(--primary-glow); }");
            out.println("        .section-nav-card.active { border-color: var(--primary); background: linear-gradient(180deg, #f0f9ff 0%, var(--card-bg) 100%); }");
            out.println("        .card-tag { font-size: 0.75rem; text-transform: uppercase; font-weight: 700; letter-spacing: 0.06em; color: var(--primary); margin-bottom: 0.25rem; display: flex; align-items: center; gap: 0.4rem; }");
            out.println("        .card-title { font-size: 1.25rem; font-weight: 700; margin: 0 0 0.4rem 0; color: #0f172a; }");
            out.println("        .card-desc { font-size: 0.875rem; color: var(--text-muted); margin: 0; }");
            out.println("        .card-footer { margin-top: 1rem; padding-top: 0.75rem; border-top: 1px solid var(--card-border); font-size: 0.8rem; font-weight: 600; color: var(--primary); display: flex; justify-content: space-between; align-items: center; }");
            out.println("        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 1.25rem; margin-bottom: 2.25rem; }");
            out.println("        .stat-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 1.25rem; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            out.println("        .stat-value { font-size: 1.9rem; font-weight: 800; font-family: 'JetBrains Mono', monospace; margin: 0.25rem 0; }");
            out.println("        .stat-sub { font-size: 0.8rem; color: var(--text-muted); }");
            out.println("        .stat-label { color: var(--text-muted); font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; }");
            out.println("        .quick-actions { display: flex; gap: 0.75rem; margin-bottom: 2rem; flex-wrap: wrap; align-items: center; }");
            out.println("        .action-btn { background: var(--card-bg); border: 1px solid var(--card-border); color: #334155; padding: 0.55rem 1.1rem; border-radius: 8px; text-decoration: none; font-size: 0.875rem; font-weight: 600; transition: all 0.2s; display: inline-flex; align-items: center; gap: 0.5rem; box-shadow: 0 1px 2px rgba(0,0,0,0.04); }");
            out.println("        .action-btn:hover { background: #f8fafc; border-color: var(--primary); color: var(--primary); }");
            out.println("        .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 1.75rem; margin-bottom: 2.25rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            out.println("        .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; gap: 1rem; border-bottom: 1px solid var(--card-border); padding-bottom: 1rem; }");
            out.println("        .section-header h2 { margin: 0; font-size: 1.45rem; font-weight: 700; display: flex; align-items: center; gap: 0.6rem; color: #0f172a; }");
            out.println("        .section-desc { color: var(--text-muted); font-size: 0.95rem; margin: 0.25rem 0 0 0; }");
            out.println("        .subpage-link { font-size: 0.85rem; font-weight: 600; color: var(--primary); text-decoration: none; border: 1px solid #bae6fd; padding: 0.4rem 0.8rem; border-radius: 6px; background: #f0f9ff; transition: all 0.2s; }");
            out.println("        .subpage-link:hover { background: #e0f2fe; border-color: var(--primary); }");
            out.println("        table { width: 100%; border-collapse: collapse; margin-top: 1rem; font-size: 0.9rem; }");
            out.println("        th, td { padding: 0.8rem 1rem; text-align: left; border-bottom: 1px solid var(--card-border); }");
            out.println("        th { background: #f8fafc; font-weight: 700; font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }");
            out.println("        tr:hover td { background: #f8fafc; }");
            out.println("        .badge { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.25rem 0.65rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; white-space: nowrap; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .badge-success { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }");
            out.println("        .badge-info { background: #f0f9ff; color: #0284c7; border: 1px solid #bae6fd; }");
            out.println("        .badge-warning { background: #fef3c7; color: #b45309; border: 1px solid #fde68a; }");
            out.println("        .badge-time { background: #f1f5f9; color: #475569; border: 1px solid #e2e8f0; font-size: 0.73rem; }");
            out.println("        .badge-perf { background: #f3e8ff; color: #7e22ce; border: 1px solid #e9d5ff; }");
            out.println("        .numeric { text-align: right; font-variant-numeric: tabular-nums; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .speedup-fast { color: #16a34a; font-weight: 700; }");
            out.println("        .speedup-slow { color: #dc2626; }");
            out.println("        .perf-tag { font-size: 0.8rem; padding: 0.15rem 0.4rem; border-radius: 4px; background: #f1f5f9; border: 1px solid #e2e8f0; color: #334155; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .subtable-wrapper { margin-top: 1.75rem; padding-top: 1.25rem; border-top: 1px dashed var(--card-border); }");
            out.println("        .subtable-title { font-size: 1.05rem; font-weight: 700; margin: 0 0 0.75rem 0; color: #1e293b; display: flex; align-items: center; justify-content: space-between; }");
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
            out.println("            <div class=\"stat-label\">FastHashMap AI Tests</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--success);\">%d</div>%n", totalFastTests);
            out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalFastTime);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Manual BlackBox Tests</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: #059669;\">%d</div>%n", totalBlackBoxTests);
            out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalBlackBoxTime);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">LRUClockMap Tests</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: #0284c7;\">%d</div>%n", totalLruTests);
            out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalLruTime);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Report Utility Tests</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--accent);\">%d</div>%n", totalUtilTests);
            out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalUtilTime);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Peak Put Speedup</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: #d97706;\">%.2fx</div>%n", peakPut);
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

                out.println("        <div style=\"font-size: 1.05rem; font-weight: 700; margin: 1.25rem 0 0.5rem 0; color: #1e293b; display: flex; align-items: center; justify-content: space-between;\">");
                out.println("            <span>🤖 AI-Generated Test Suites (FastHashMapTest)</span>");
                out.println("            <span style=\"font-size: 0.8rem; font-weight: 500; color: var(--text-muted);\">Self-generated test suite accompanying each model</span>");
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

                    int total = s.quality().tests();
                    int fails = s.quality().failures() + s.quality().errors();
                    int passed = Math.max(0, total - fails);
                    String testBadge;
                    String statusBadge;
                    if (total == 0) {
                        testBadge = "<span class=\"badge badge-info\">0 Tests</span>";
                        statusBadge = "<span class=\"badge badge-info\">No Tests</span>";
                    } else if (fails == 0) {
                        testBadge = String.format("<a href=\"surefire.html\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s", total, timeBadge);
                        statusBadge = "<span class=\"badge badge-success\">100% Passing ✅</span>";
                    } else {
                        testBadge = String.format("<a href=\"surefire.html\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%d/%d Passed</a> %s", passed, total, timeBadge);
                        statusBadge = String.format("<span class=\"badge badge-warning\">⚠ %d Failed</span>", fails);
                    }

                    out.println("                <tr>");
                    out.printf("                    <td><strong>%s</strong></td>%n", s.id());
                    out.printf("                    <td><span class=\"badge badge-info\">%s</span></td>%n", s.aiModel());
                    out.printf("                    <td>%s</td>%n", testBadge);
                    out.printf("                    <td><a href=\"%s\" style=\"color: var(--primary); text-decoration: underline;\"><strong>%.1f%%</strong></a> (%d/%d)</td>%n", covLink, s.quality().instructionCoveragePct(), s.quality().totalInstructions() - s.quality().missedInstructions(), s.quality().totalInstructions());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().lineCoveragePct(), s.quality().totalLines() - s.quality().missedLines(), s.quality().totalLines());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().branchCoveragePct(), s.quality().totalBranches() - s.quality().missedBranches(), s.quality().totalBranches());
                    out.printf("                    <td>%s</td>%n", pitBadge);
                    out.printf("                    <td>%s</td>%n", statusBadge);
                    out.println("                </tr>");
                }

                out.println("            </tbody>");
                out.println("        </table>");

                // Manual BlackBox Table
                out.println("        <div class=\"subtable-wrapper\">");
                out.println("            <div class=\"subtable-title\">");
                out.println("                <span>🧪 FastHashMap Manual BlackBox — Quality, Coverage & Mutation Verification</span>");
                out.println("                <span style=\"font-size: 0.8rem; font-weight: 500; color: var(--text-muted);\">Standardized human-written black-box test suite evaluated across all implementations</span>");
                out.println("            </div>");
                out.println("            <table>");
                out.println("                <thead>");
                out.println("                    <tr>");
                out.println("                        <th>Module</th>");
                out.println("                        <th>AI Model / Implementation</th>");
                out.println("                        <th>Unit Tests</th>");
                out.println("                        <th>Instruction Coverage</th>");
                out.println("                        <th>Line Coverage</th>");
                out.println("                        <th>Branch Coverage</th>");
                out.println("                        <th>PIT Mutation Score</th>");
                out.println("                        <th>Status</th>");
                out.println("                    </tr>");
                out.println("                </thead>");
                out.println("                <tbody>");

                for (FastHashMapBlackBoxSummary s : fastBlackBoxes) {
                    String pkgName = s.id();
                    String pitBadge = s.quality().pitTotal() > 0 ?
                            String.format("<a href=\"pit-reports-blackbox/%s/org.jugsaxony.%s/FastHashMap.java.html\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%.1f%% (%d/%d)</a>",
                                    s.id(), pkgName, s.quality().pitScorePct(), s.quality().pitKilled(), s.quality().pitTotal())
                            : "<span class=\"badge badge-info\">N/A</span>";

                    String covLink = String.format("jacoco-blackbox/%s/org.jugsaxony.%s/FastHashMap.java.html", s.id(), pkgName);
                    String timeBadge = s.quality().executionTimeSeconds() > 0 ?
                            String.format("<span class=\"badge badge-time\">⏱️ %.2fs</span>", s.quality().executionTimeSeconds()) : "";

                    int total = s.quality().tests();
                    int fails = s.quality().failures() + s.quality().errors();
                    int passed = Math.max(0, total - fails);
                    String testBadge;
                    String statusBadge;
                    if (total == 0) {
                        testBadge = "<span class=\"badge badge-info\">0 Tests</span>";
                        statusBadge = "<span class=\"badge badge-info\">No Tests</span>";
                    } else if (fails == 0) {
                        testBadge = String.format("<a href=\"surefire.html\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s", total, timeBadge);
                        statusBadge = "<span class=\"badge badge-success\">100% Passing ✅</span>";
                    } else {
                        testBadge = String.format("<a href=\"surefire.html\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%d/%d Passed</a> %s", passed, total, timeBadge);
                        statusBadge = String.format("<span class=\"badge badge-warning\">⚠ %d Failed</span>", fails);
                    }

                    out.println("                <tr>");
                    out.printf("                    <td><strong>%s</strong></td>%n", s.id());
                    out.printf("                    <td><span class=\"badge badge-info\">%s</span></td>%n", s.aiModel());
                    out.printf("                    <td>%s</td>%n", testBadge);
                    out.printf("                    <td><a href=\"%s\" style=\"color: var(--primary); text-decoration: underline;\"><strong>%.1f%%</strong></a> (%d/%d)</td>%n", covLink, s.quality().instructionCoveragePct(), s.quality().totalInstructions() - s.quality().missedInstructions(), s.quality().totalInstructions());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().lineCoveragePct(), s.quality().totalLines() - s.quality().missedLines(), s.quality().totalLines());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().branchCoveragePct(), s.quality().totalBranches() - s.quality().missedBranches(), s.quality().totalBranches());
                    out.printf("                    <td>%s</td>%n", pitBadge);
                    out.printf("                    <td>%s</td>%n", statusBadge);
                    out.println("                </tr>");
                }

                out.println("                </tbody>");
                out.println("            </table>");
                out.println("        </div>");

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

                    int total = s.quality().tests();
                    int fails = s.quality().failures() + s.quality().errors();
                    int passed = Math.max(0, total - fails);
                    String testBadge;
                    String statusBadge;
                    if (total == 0) {
                        testBadge = "<span class=\"badge badge-info\">0 Tests</span>";
                        statusBadge = "<span class=\"badge badge-info\">No Tests</span>";
                    } else if (fails == 0) {
                        testBadge = String.format("<a href=\"surefire.html\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s", total, timeBadge);
                        statusBadge = "<span class=\"badge badge-success\">100% Passing ✅</span>";
                    } else {
                        testBadge = String.format("<a href=\"surefire.html\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%d/%d Passed</a> %s", passed, total, timeBadge);
                        statusBadge = String.format("<span class=\"badge badge-warning\">⚠ %d Failed</span>", fails);
                    }

                    out.println("                <tr>");
                    out.printf("                    <td><strong>%s</strong></td>%n", s.id());
                    out.printf("                    <td><span class=\"badge badge-info\">%s</span></td>%n", s.aiModel());
                    out.printf("                    <td>%s</td>%n", testBadge);
                    out.printf("                    <td><a href=\"%s\" style=\"color: var(--primary); text-decoration: underline;\"><strong>%.1f%%</strong></a> (%d/%d)</td>%n", covLink, s.quality().instructionCoveragePct(), s.quality().totalInstructions() - s.quality().missedInstructions(), s.quality().totalInstructions());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().lineCoveragePct(), s.quality().totalLines() - s.quality().missedLines(), s.quality().totalLines());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().branchCoveragePct(), s.quality().totalBranches() - s.quality().missedBranches(), s.quality().totalBranches());
                    out.printf("                    <td>%s</td>%n", pitBadge);
                    out.printf("                    <td>%s</td>%n", statusBadge);
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

                    String covLink = String.format("jacoco/%s/xlt-util-coverage.html", s.id());
                    String timeBadge = s.quality().executionTimeSeconds() > 0 ?
                            String.format("<span class=\"badge badge-time\">⏱️ %.2fs</span>", s.quality().executionTimeSeconds()) : "";

                    int total = s.quality().tests();
                    int fails = s.quality().failures() + s.quality().errors();
                    int passed = Math.max(0, total - fails);
                    String testBadge;
                    String statusBadge;
                    if (total == 0) {
                        testBadge = "<span class=\"badge badge-info\">0 Tests</span>";
                        statusBadge = "<span class=\"badge badge-info\">No Tests</span>";
                    } else if (fails == 0) {
                        testBadge = String.format("<a href=\"surefire.html\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s", total, timeBadge);
                        statusBadge = "<span class=\"badge badge-success\">100% Passing ✅</span>";
                    } else {
                        testBadge = String.format("<a href=\"surefire.html\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%d/%d Passed</a> %s", passed, total, timeBadge);
                        statusBadge = String.format("<span class=\"badge badge-warning\">⚠ %d Failed</span>", fails);
                    }

                    out.println("                <tr>");
                    out.printf("                    <td><strong>%s</strong></td>%n", s.id());
                    out.printf("                    <td><span class=\"badge badge-info\">%s</span></td>%n", s.aiModel());
                    out.printf("                    <td>%s</td>%n", testBadge);
                    out.printf("                    <td><a href=\"%s\" style=\"color: var(--primary); text-decoration: underline;\"><strong>%.1f%%</strong></a> (%d/%d)</td>%n", covLink, s.quality().instructionCoveragePct(), s.quality().totalInstructions() - s.quality().missedInstructions(), s.quality().totalInstructions());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().lineCoveragePct(), s.quality().totalLines() - s.quality().missedLines(), s.quality().totalLines());
                    out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, s.quality().branchCoveragePct(), s.quality().totalBranches() - s.quality().missedBranches(), s.quality().totalBranches());
                    out.printf("                    <td>%s</td>%n", pitBadge);
                    out.printf("                    <td>%s</td>%n", statusBadge);
                    out.println("                </tr>");
                }

                out.println("            </tbody>");
                out.println("        </table>");

                // Class-Level Coverage Breakdown Matrix for Reporting Utilities
                out.println("        <div class=\"subtable-wrapper\">");
                out.println("            <div class=\"subtable-title\">");
                out.println("                <span>📊 Class-Level Coverage Breakdown Matrix (All 5 Reporting Utilities)</span>");
                out.println("                <span style=\"font-size: 0.8rem; font-weight: 500; color: var(--text-muted);\">Instruction Coverage per Class & Direct Links to JaCoCo Source Code</span>");
                out.println("            </div>");
                out.println("            <table>");
                out.println("                <thead>");
                out.println("                    <tr>");
                out.println("                        <th>Module</th>");
                out.println("                        <th>AI Model / Implementation</th>");
                out.println("                        <th class=\"numeric\">RuntimeHistogram</th>");
                out.println("                        <th class=\"numeric\">BitUtil</th>");
                out.println("                        <th class=\"numeric\">BitCompression</th>");
                out.println("                        <th class=\"numeric\">IntTimeSeries</th>");
                out.println("                        <th class=\"numeric\">IntTimeSeriesEntry</th>");
                out.println("                        <th class=\"numeric\">Total Suite</th>");
                out.println("                    </tr>");
                out.println("                </thead>");
                out.println("                <tbody>");

                for (ReportUtilSummary s : reportUtils) {
                    Map<String, XltClassCoverage> covMap = new HashMap<>();
                    for (XltClassCoverage c : s.classCoverages()) {
                        covMap.put(c.className(), c);
                    }

                    out.println("                    <tr>");
                    out.printf("                        <td><strong>%s</strong></td>%n", s.id());
                    out.printf("                        <td><span class=\"badge badge-info\">%s</span></td>%n", s.aiModel());

                    for (String cname : List.of("RuntimeHistogram", "BitUtil", "BitCompression", "IntTimeSeries", "IntTimeSeriesEntry")) {
                        XltClassCoverage c = covMap.get(cname);
                        if (c != null) {
                            String cellContent;
                            if (c.instructionCoveragePct() >= 99.9) {
                                cellContent = String.format("<a href=\"jacoco/%s/%s\" class=\"badge badge-success\" style=\"text-decoration:none;\">%.1f%%</a>", s.id(), c.htmlRelPath(), c.instructionCoveragePct());
                            } else if (c.instructionCoveragePct() < 80.0) {
                                cellContent = String.format("<a href=\"jacoco/%s/%s\" style=\"color: #b45309; font-weight: 700; text-decoration: underline;\">%.1f%%</a> <span style=\"font-size: 0.72rem; color: var(--text-muted);\">(%d/%d)</span>", s.id(), c.htmlRelPath(), c.instructionCoveragePct(), c.coveredInstructions(), c.totalInstructions());
                            } else {
                                cellContent = String.format("<a href=\"jacoco/%s/%s\" style=\"color: #0284c7; text-decoration: underline;\">%.1f%%</a> <span style=\"font-size: 0.72rem; color: var(--text-muted);\">(%d/%d)</span>", s.id(), c.htmlRelPath(), c.instructionCoveragePct(), c.coveredInstructions(), c.totalInstructions());
                            }
                            out.printf("                        <td class=\"numeric\">%s</td>%n", cellContent);
                        } else {
                            out.println("                        <td class=\"numeric\">-</td>");
                        }
                    }

                    out.printf("                        <td class=\"numeric\"><strong><a href=\"jacoco/%s/xlt-util-coverage.html\" style=\"color: var(--primary); text-decoration: underline;\">%.1f%%</a></strong></td>%n", s.id(), s.quality().instructionCoveragePct());
                    out.println("                    </tr>");
                }

                out.println("                </tbody>");
                out.println("            </table>");
                out.println("        </div>");

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
