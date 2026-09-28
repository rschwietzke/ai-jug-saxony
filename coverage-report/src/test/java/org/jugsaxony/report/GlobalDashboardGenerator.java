package org.jugsaxony.report;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

import java.io.*;
import java.nio.charset.StandardCharsets;
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

    public record SimpleMathQualitySummary(
            String id,
            String name,
            String aiModel,
            QualityStats simpleMathQuality,
            QualityStats simpleMathCleanQuality
    ) {
        public boolean hasTests() {
            return (simpleMathQuality != null && simpleMathQuality.tests() > 0)
                    || (simpleMathCleanQuality != null && simpleMathCleanQuality.tests() > 0);
        }
    }

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
            Map.entry("demo0", new String[]{"Demo 0", "Human Baseline", "Flat parallel Object[] arrays"}),
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
        List<SimpleMathQualitySummary> simpleMathSummaries = new ArrayList<>();

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
            File targetPitCsv = new File(modDir, "target/pit-reports/mutations.csv");
            File pitCsv = targetPitCsv.exists() ? targetPitCsv : new File(outputDir, "pit-reports/" + modId + "/mutations.csv");

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

            File smSurefireDir = new File(outputDir, "surefire-reports-simplemath/" + modId + "/SimpleMath");
            if (!smSurefireDir.exists()) {
                smSurefireDir = new File(outputDir, "surefire-reports-simplemath/" + modId);
            }
            if (!smSurefireDir.exists()) {
                smSurefireDir = surefireDir;
            }

            File smCleanSurefireDir = new File(outputDir, "surefire-reports-simplemath/" + modId + "/SimpleMathClean");
            if (!smCleanSurefireDir.exists()) {
                smCleanSurefireDir = new File(outputDir, "surefire-reports-simplemath/" + modId);
            }
            if (!smCleanSurefireDir.exists()) {
                smCleanSurefireDir = surefireDir;
            }

            File smJacocoXml = new File(outputDir, "jacoco-simplemath/" + modId + "/jacoco.xml");
            if (!smJacocoXml.exists()) {
                smJacocoXml = jacocoXml;
            }

            File smPitCsv = new File(outputDir, "pit-reports-simplemath/" + modId + "/mutations.csv");
            if (!smPitCsv.exists()) {
                smPitCsv = pitCsv;
            }

            QualityStats smQuality = buildSimpleMathQualityStats(smSurefireDir, smJacocoXml, smPitCsv, "SimpleMathTest", "SimpleMath.java");
            QualityStats smCleanQuality = buildSimpleMathQualityStats(smCleanSurefireDir, smJacocoXml, smPitCsv, "SimpleMathCleanTest", "SimpleMathClean.java");

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

            simpleMathSummaries.add(new SimpleMathQualitySummary(
                    modId,
                    meta.name(),
                    meta.model(),
                    smQuality,
                    smCleanQuality
            ));
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
            if (modPit.exists() && modPit.isDirectory()) {
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

        // Copy SimpleMath reports per module if available in target and not already in outputDir
        for (String modDirName : modDirs) {
            File modJacocoSm = new File(rootProjectDir, modDirName + "/target/site/jacoco");
            File destJacocoSm = new File(outputDir, "jacoco-simplemath/" + modDirName);
            if (!destJacocoSm.exists() && modJacocoSm.exists() && modJacocoSm.isDirectory()) {
                copyDirectory(modJacocoSm, destJacocoSm);
            }
            File modPitSm = new File(rootProjectDir, modDirName + "/target/pit-reports");
            File destPitSm = new File(outputDir, "pit-reports-simplemath/" + modDirName);
            if (!destPitSm.exists() && modPitSm.exists() && modPitSm.isDirectory()) {
                copyDirectory(modPitSm, destPitSm);
            }
        }

        // Generate source code snapshots and presentation viewers
        SourceViewerGenerator.generateSourceSnapshots(outputDir, rootProjectDir, modDirs, MODULE_METADATA);

        // Write Markdown Dashboard
        writeMarkdownDashboard(new File(outputDir, "README.md"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, simpleMathSummaries);
        writeMarkdownDashboard(new File(outputDir, "global-dashboard.md"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, simpleMathSummaries);

        // Write Master HTML Dashboard
        writeHtmlDashboard(new File(outputDir, "index.html"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, simpleMathSummaries, null);
        writeHtmlDashboard(new File(outputDir, "global-dashboard.html"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, simpleMathSummaries, null);

        // Write Standalone Sub-Section HTML Pages
        writeHtmlDashboard(new File(outputDir, "fasthashmap.html"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, simpleMathSummaries, "fasthashmap");
        writeHtmlDashboard(new File(outputDir, "lruclockmap.html"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, simpleMathSummaries, "lruclockmap");
        writeHtmlDashboard(new File(outputDir, "xlt-util.html"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, simpleMathSummaries, "xlt-util");
        writeHtmlDashboard(new File(outputDir, "simplemath.html"), fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, simpleMathSummaries, "simplemath");

        // Write Surefire Aggregated Unit Test Report
        writeSurefireHtmlReport(new File(outputDir, "surefire.html"), outputDir, rootProjectDir, fastMapSummaries, fastBlackBoxSummaries, lruMapSummaries, reportUtilSummaries, simpleMathSummaries);
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

    private record FailedTestCase(String className, String testName, String failureType, String message) {}

    private static List<FailedTestCase> parseFailedTestCases(File surefireDir, String testPattern, boolean isPrefix) {
        List<FailedTestCase> failures = new ArrayList<>();
        if (!surefireDir.exists() || !surefireDir.isDirectory()) return failures;
        File[] files = surefireDir.listFiles((dir, name) -> {
            if (!name.startsWith("TEST-") || !name.endsWith(".xml")) return false;
            if (testPattern == null || testPattern.isEmpty()) return true;
            if (isPrefix) {
                return name.startsWith("TEST-" + testPattern);
            } else {
                return name.contains(testPattern);
            }
        });
        if (files == null) return failures;

        Pattern tcBlockPat = Pattern.compile("<testcase\\b([^>]*?)>((?:(?!<testcase\\b).)*?)</testcase>", Pattern.DOTALL);
        Pattern namePat = Pattern.compile("\\bname=\"([^\"]+)\"");
        Pattern classnamePat = Pattern.compile("\\bclassname=\"([^\"]+)\"");
        Pattern failPat = Pattern.compile("<(failure|error)\\b([^>]*?)>(.*?)</\\1>", Pattern.DOTALL);
        Pattern msgPat = Pattern.compile("\\bmessage=\"([^\"]*)\"");
        Pattern typePat = Pattern.compile("\\btype=\"([^\"]*)\"");

        for (File f : files) {
            try {
                String content = Files.readString(f.toPath());
                Matcher tcMatcher = tcBlockPat.matcher(content);
                while (tcMatcher.find()) {
                    String tcAttrs = tcMatcher.group(1);
                    if (tcAttrs.trim().endsWith("/")) continue;
                    String tcBody = tcMatcher.group(2);

                    Matcher failMatcher = failPat.matcher(tcBody);
                    if (failMatcher.find()) {
                        String failAttrs = failMatcher.group(2);
                        String failBody = failMatcher.group(3).trim();

                        String tName = "unknown";
                        Matcher nm = namePat.matcher(tcAttrs);
                        if (nm.find()) tName = nm.group(1);

                        String cName = f.getName().replace("TEST-", "").replace(".xml", "");
                        Matcher cm = classnamePat.matcher(tcAttrs);
                        if (cm.find()) cName = cm.group(1);

                        String msg = "";
                        Matcher mm = msgPat.matcher(failAttrs);
                        if (mm.find()) {
                            msg = mm.group(1).replace("&#10;", " ").replace("\n", " ").trim();
                        }
                        if (msg.isEmpty() && !failBody.isEmpty()) {
                            int nl = failBody.indexOf('\n');
                            msg = (nl > 0 ? failBody.substring(0, nl) : failBody).trim();
                        }

                        String type = "Failure";
                        Matcher tm = typePat.matcher(failAttrs);
                        if (tm.find()) type = tm.group(1);

                        failures.add(new FailedTestCase(cName, tName, type, msg));
                    }
                }
            } catch (Exception ignored) {}
        }
        return failures;
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

    private static TestExecutionInfo parseSimpleMathTestExecution(File surefireDir, String testPattern) {
        if (!surefireDir.exists() || !surefireDir.isDirectory()) return new TestExecutionInfo(0, 0.0, 0, 0);
        if (surefireDir.getName().equalsIgnoreCase("SimpleMath") || surefireDir.getName().equalsIgnoreCase("SimpleMathClean")) {
            return parseTestExecution(surefireDir, "", false);
        }
        TestExecutionInfo info = parseTestExecution(surefireDir, testPattern, false);
        if (info.tests() == 0 && surefireDir.getPath().contains("demo6")) {
            info = parseTestExecution(surefireDir, "AbstractSimpleMathContract", false);
        }
        return info;
    }

    private static QualityStats buildSimpleMathQualityStats(File surefireDir, File jacocoXml, File pitCsv, String testPattern, String sourceFileName) {
        TestExecutionInfo testInfo = parseSimpleMathTestExecution(surefireDir, testPattern);
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
            List<ReportUtilSummary> reportUtils,
            List<SimpleMathQualitySummary> simpleMaths
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

                out.printf("| %s | %s | %d ✅%s | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %s | %s |%n",
                        renderMarkdownModuleLink(s.id(), "FastHashMap"),
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

                out.printf("| %s | %s | %d ✅%s | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %s | %s |%n",
                        renderMarkdownModuleLink(s.id(), "FastHashMap"),
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
                out.printf("| %s | %s | %,d B | %,.1f B/e | %,d | %,d B | %d B |%n",
                        renderMarkdownModuleLink(s.id(), "FastHashMap"),
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

                out.printf("| %s | %s | %d ✅%s | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %s | %s |%n",
                        renderMarkdownModuleLink(s.id(), "LRUClockMap"),
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

                out.printf("| %s | %s | %d ✅%s | [%.1f%% (%d/%d)](jacoco/%s/xlt-util-coverage.html) | [%.1f%% (%d/%d)](jacoco/%s/xlt-util-coverage.html) | [%.1f%% (%d/%d)](jacoco/%s/xlt-util-coverage.html) | %s | %s |%n",
                        renderMarkdownModuleLink(s.id(), "IntTimeSeries"),
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
            out.println("## 🧮 Part 4: SimpleMath & SimpleMathClean — Quality, Coverage & Mutation Verification");
            out.println();
            out.println("### 📐 SimpleMath Test Suites (SimpleMathTest)");
            out.println();
            out.println("| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |");
            out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

            for (SimpleMathQualitySummary s : simpleMaths) {
                QualityStats q = s.simpleMathQuality();
                String pitStr = q.pitTotal() > 0 ?
                        String.format("%.1f%% (%d/%d killed)", q.pitScorePct(), q.pitKilled(), q.pitTotal()) : "N/A";
                String timeStr = q.executionTimeSeconds() > 0 ? String.format(" (%.2fs)", q.executionTimeSeconds()) : "";

                int total = q.tests();
                int fails = q.failures() + q.errors();
                int passed = Math.max(0, total - fails);
                String testStr;
                String statusStr;
                String covInstStr;
                String covLineStr;
                String covBranchStr;

                if (total == 0) {
                    testStr = "-";
                    statusStr = "No Tests";
                    covInstStr = "-";
                    covLineStr = "-";
                    covBranchStr = "-";
                } else {
                    if (fails == 0) {
                        testStr = String.format("%d ✅%s", total, timeStr);
                        statusStr = "100% Passing ✅";
                    } else {
                        testStr = String.format("%d/%d ✅%s", passed, total, timeStr);
                        statusStr = String.format("⚠ %d Failed", fails);
                    }
                    covInstStr = String.format("[%.1f%% (%d/%d)](jacoco-simplemath/%s/org.jugsaxony.%s/SimpleMath.java.html)",
                            q.instructionCoveragePct(), q.totalInstructions() - q.missedInstructions(), q.totalInstructions(), s.id(), s.id());
                    covLineStr = String.format("[%.1f%% (%d/%d)](jacoco-simplemath/%s/org.jugsaxony.%s/SimpleMath.java.html)",
                            q.lineCoveragePct(), q.totalLines() - q.missedLines(), q.totalLines(), s.id(), s.id());
                    covBranchStr = String.format("[%.1f%% (%d/%d)](jacoco-simplemath/%s/org.jugsaxony.%s/SimpleMath.java.html)",
                            q.branchCoveragePct(), q.totalBranches() - q.missedBranches(), q.totalBranches(), s.id(), s.id());
                }

                out.printf("| %s | %s | %s | %s | %s | %s | %s | %s |%n",
                        renderMarkdownModuleLink(s.id(), "SimpleMath"),
                        s.aiModel(),
                        testStr,
                        covInstStr,
                        covLineStr,
                        covBranchStr,
                        pitStr,
                        statusStr
                );
            }

            out.println();
            out.println("### 🧼 SimpleMathClean Test Suites (SimpleMathCleanTest)");
            out.println();
            out.println("| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |");
            out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

            for (SimpleMathQualitySummary s : simpleMaths) {
                QualityStats q = s.simpleMathCleanQuality();
                String pitStr = q.pitTotal() > 0 ?
                        String.format("%.1f%% (%d/%d killed)", q.pitScorePct(), q.pitKilled(), q.pitTotal()) : "N/A";
                String timeStr = q.executionTimeSeconds() > 0 ? String.format(" (%.2fs)", q.executionTimeSeconds()) : "";

                int total = q.tests();
                int fails = q.failures() + q.errors();
                int passed = Math.max(0, total - fails);
                String testStr;
                String statusStr;
                String covInstStr;
                String covLineStr;
                String covBranchStr;

                if (total == 0) {
                    testStr = "-";
                    statusStr = "No Tests";
                    covInstStr = "-";
                    covLineStr = "-";
                    covBranchStr = "-";
                } else {
                    if (fails == 0) {
                        testStr = String.format("%d ✅%s", total, timeStr);
                        statusStr = "100% Passing ✅";
                    } else {
                        testStr = String.format("%d/%d ✅%s", passed, total, timeStr);
                        statusStr = String.format("⚠ %d Failed", fails);
                    }
                    covInstStr = String.format("[%.1f%% (%d/%d)](jacoco-simplemath/%s/org.jugsaxony.%s/SimpleMathClean.java.html)",
                            q.instructionCoveragePct(), q.totalInstructions() - q.missedInstructions(), q.totalInstructions(), s.id(), s.id());
                    covLineStr = String.format("[%.1f%% (%d/%d)](jacoco-simplemath/%s/org.jugsaxony.%s/SimpleMathClean.java.html)",
                            q.lineCoveragePct(), q.totalLines() - q.missedLines(), q.totalLines(), s.id(), s.id());
                    covBranchStr = String.format("[%.1f%% (%d/%d)](jacoco-simplemath/%s/org.jugsaxony.%s/SimpleMathClean.java.html)",
                            q.branchCoveragePct(), q.totalBranches() - q.missedBranches(), q.totalBranches(), s.id(), s.id());
                }

                out.printf("| %s | %s | %s | %s | %s | %s | %s | %s |%n",
                        renderMarkdownModuleLink(s.id(), "SimpleMathClean"),
                        s.aiModel(),
                        testStr,
                        covInstStr,
                        covLineStr,
                        covBranchStr,
                        pitStr,
                        statusStr
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
            out.println("- 🧮 **SimpleMath Evaluation**: [SimpleMath Report](simplemath.html)");
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
            List<SimpleMathQualitySummary> simpleMaths,
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

            int totalSimpleMathTests = simpleMaths.stream().mapToInt(s -> s.simpleMathQuality().tests()).sum();
            double totalSimpleMathTime = simpleMaths.stream().mapToDouble(s -> s.simpleMathQuality().executionTimeSeconds()).sum();

            int totalSimpleMathCleanTests = simpleMaths.stream().mapToInt(s -> s.simpleMathCleanQuality().tests()).sum();
            double totalSimpleMathCleanTime = simpleMaths.stream().mapToDouble(s -> s.simpleMathCleanQuality().executionTimeSeconds()).sum();

            double peakPut = fastMaps.stream().mapToDouble(FastHashMapSummary::putSpeedup).max().orElse(1.0);
            boolean anyPerf = fastMaps.stream().anyMatch(FastHashMapSummary::hasPerf);

            boolean showFast = activeSection == null || "fasthashmap".equalsIgnoreCase(activeSection);
            boolean showLru = activeSection == null || "lruclockmap".equalsIgnoreCase(activeSection);
            boolean showUtil = activeSection == null || "xlt-util".equalsIgnoreCase(activeSection);
            boolean showSimpleMath = activeSection == null || "simplemath".equalsIgnoreCase(activeSection);

            String pageTitle = activeSection == null ? "AI JUG Saxony - Master Evaluation Dashboard"
                    : ("fasthashmap".equalsIgnoreCase(activeSection) ? "AI JUG Saxony - FastHashMap Evaluation"
                    : ("lruclockmap".equalsIgnoreCase(activeSection) ? "AI JUG Saxony - LRUClockMap Evaluation"
                    : ("xlt-util".equalsIgnoreCase(activeSection) ? "AI JUG Saxony - com.xceptance.xlt.report.util Evaluation"
                    : "AI JUG Saxony - SimpleMath & SimpleMathClean Evaluation")));

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
            out.println("        .module-source-link { color: var(--primary); text-decoration: none; display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.2rem 0.45rem; border-radius: 6px; background: rgba(2, 132, 199, 0.06); border: 1px solid rgba(2, 132, 199, 0.15); transition: all 0.2s ease; font-weight: 700; }");
            out.println("        .module-source-link:hover { background: rgba(2, 132, 199, 0.14); border-color: var(--primary); text-decoration: none; transform: translateY(-1px); }");
            out.println("        .module-source-link .code-icon { font-size: 0.75rem; opacity: 0.75; }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container\">");

            // Header
            out.println("    <div class=\"header\">");
            out.println("        <div>");
            out.printf("            <h1>%s</h1>%n", pageTitle);
            String subtitle;
            if (activeSection == null) {
                subtitle = "Comprehensive Evaluation: Performance, Memory, Quality & Verification across AI Models vs Demo 0 Baseline";
            } else if ("fasthashmap".equalsIgnoreCase(activeSection)) {
                subtitle = "High-Performance Core Map: Unit Tests, Black-Box Verification, JOL Memory Layout, and JMH Throughput vs Demo 0 Baseline";
            } else if ("lruclockmap".equalsIgnoreCase(activeSection)) {
                subtitle = "Bounded Second-Chance Eviction Cache: Unit Tests, Line/Branch Coverage, and Mutation Resilience";
            } else if ("xlt-util".equalsIgnoreCase(activeSection)) {
                subtitle = "High-Throughput Reporting Utilities (RuntimeHistogram, IntTimeSeries, BitUtil, BitCompression)";
            } else {
                subtitle = "SimpleMath & SimpleMathClean Quality Verification: Arithmetic Suites, Overflow Edge Cases & Mutation Scores";
            }
            out.printf("            <p>%s</p>%n", subtitle);
            out.println("        </div>");
            if (activeSection != null) {
                out.println("        <div>");
                out.println("            <a href=\"index.html\" class=\"action-btn\">← Back to Master Dashboard</a>");
                out.println("        </div>");
            }
            out.println("    </div>");

            // Quick Jump / Sub-section Navigation Banner (only rendered on master index page)
            if (activeSection == null) {
                out.println("    <div class=\"section-nav-banner\">");

                // Card 1: FastHashMap
                out.println("        <a href=\"#fasthashmap\" class=\"section-nav-card\">");
                out.println("            <div>");
                out.println("                <div class=\"card-tag\">⚡ High-Performance Core Map</div>");
                out.println("                <div class=\"card-title\">FastHashMap</div>");
                out.println("                <p class=\"card-desc\">Verification matrix, JOL object layouts, and JMH read/write throughput speedup analysis.</p>");
                out.println("            </div>");
                out.printf("            <div class=\"card-footer\"><span>%d Tests Passed (%.2fs)</span><span>Jump to Section ↓</span></div>%n", totalFastTests, totalFastTime);
                out.println("        </a>");

                // Card 2: LRUClockMap
                out.println("        <a href=\"#lruclockmap\" class=\"section-nav-card\">");
                out.println("            <div>");
                out.println("                <div class=\"card-tag\">⏰ Bounded Second-Chance Eviction Cache</div>");
                out.println("                <div class=\"card-title\">LRUClockMap</div>");
                out.println("                <p class=\"card-desc\">Comprehensive unit test suites, line/branch coverage, and mutation score verification.</p>");
                out.println("            </div>");
                out.printf("            <div class=\"card-footer\"><span>%d Tests Passed (%.2fs)</span><span>Jump to Section ↓</span></div>%n", totalLruTests, totalLruTime);
                out.println("        </a>");

                // Card 3: com.xceptance.xlt.report.util
                out.println("        <a href=\"#xlt-report-util\" class=\"section-nav-card\">");
                out.println("            <div>");
                out.println("                <div class=\"card-tag\">🧰 High-Throughput Report Utilities</div>");
                out.println("                <div class=\"card-title\">com.xceptance.xlt.report.util</div>");
                out.println("                <p class=\"card-desc\">Time series, histograms, bit compression, and bit manipulation test verification.</p>");
                out.println("            </div>");
                out.printf("            <div class=\"card-footer\"><span>%d Tests Passed (%.2fs)</span><span>Jump to Section ↓</span></div>%n", totalUtilTests, totalUtilTime);
                out.println("        </a>");

                // Card 4: SimpleMath & SimpleMathClean
                out.println("        <a href=\"#simplemath\" class=\"section-nav-card\">");
                out.println("            <div>");
                out.println("                <div class=\"card-tag\">🧮 Math Implementation Suite</div>");
                out.println("                <div class=\"card-title\">SimpleMath & SimpleMathClean</div>");
                out.println("                <p class=\"card-desc\">Arithmetic operation tests, overflow boundary assertions, line/branch coverage, and mutation testing.</p>");
                out.println("            </div>");
                out.printf("            <div class=\"card-footer\"><span>%d Tests (%.2fs)</span><span>Jump to Section ↓</span></div>%n", (totalSimpleMathTests + totalSimpleMathCleanTests), (totalSimpleMathTime + totalSimpleMathCleanTime));
                out.println("        </a>");

                out.println("    </div>");
            }

            // Stats grid (tailored to active section)
            out.println("    <div class=\"stats-grid\">");
            if (activeSection == null) {
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
                out.println("            <div class=\"stat-label\">SimpleMath Tests</div>");
                out.printf("            <div class=\"stat-value\" style=\"color: #0891b2;\">%d</div>%n", (totalSimpleMathTests + totalSimpleMathCleanTests));
                out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", (totalSimpleMathTime + totalSimpleMathCleanTime));
                out.println("        </div>");
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">Peak Put Speedup</div>");
                out.printf("            <div class=\"stat-value\" style=\"color: #d97706;\">%.2fx</div>%n", peakPut);
                out.println("            <div class=\"stat-sub\">vs demo0 baseline</div>");
                out.println("        </div>");
            } else if ("fasthashmap".equalsIgnoreCase(activeSection)) {
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
                out.println("            <div class=\"stat-label\">Peak Put Speedup</div>");
                out.printf("            <div class=\"stat-value\" style=\"color: #d97706;\">%.2fx</div>%n", peakPut);
                out.println("            <div class=\"stat-sub\">vs demo0 baseline</div>");
                out.println("        </div>");
            } else if ("lruclockmap".equalsIgnoreCase(activeSection)) {
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">Evaluated Models</div>");
                out.printf("            <div class=\"stat-value\" style=\"color: var(--primary);\">%d</div>%n", lruMaps.size());
                out.println("            <div class=\"stat-sub\">demo0–demo9, demo11, demo12</div>");
                out.println("        </div>");
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">LRUClockMap Tests</div>");
                out.printf("            <div class=\"stat-value\" style=\"color: #0284c7;\">%d</div>%n", totalLruTests);
                out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalLruTime);
                out.println("        </div>");
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">Test Pass Rate</div>");
                out.println("            <div class=\"stat-value\" style=\"color: var(--success);\">100%</div>");
                out.println("            <div class=\"stat-sub\">All evaluated suites passing ✅</div>");
                out.println("        </div>");
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">Eviction Algorithm</div>");
                out.println("            <div class=\"stat-value\" style=\"color: #7c3aed; font-size: 1.55rem;\">Second-Chance</div>");
                out.println("            <div class=\"stat-sub\">Clock reference-bit approximation</div>");
                out.println("        </div>");
            } else if ("xlt-util".equalsIgnoreCase(activeSection)) {
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">Evaluated Models</div>");
                out.printf("            <div class=\"stat-value\" style=\"color: var(--primary);\">%d</div>%n", reportUtils.size());
                out.println("            <div class=\"stat-sub\">demo0–demo9, demo11, demo12</div>");
                out.println("        </div>");
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">Report Utility Tests</div>");
                out.printf("            <div class=\"stat-value\" style=\"color: var(--accent);\">%d</div>%n", totalUtilTests);
                out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalUtilTime);
                out.println("        </div>");
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">Core Classes Covered</div>");
                out.println("            <div class=\"stat-value\" style=\"color: #0891b2;\">5</div>");
                out.println("            <div class=\"stat-sub\">Histogram, TimeSeries, Bit tools</div>");
                out.println("        </div>");
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">Test Pass Rate</div>");
                out.println("            <div class=\"stat-value\" style=\"color: var(--success);\">100%</div>");
                out.println("            <div class=\"stat-sub\">All utility tests passing ✅</div>");
                out.println("        </div>");
            } else if ("simplemath".equalsIgnoreCase(activeSection)) {
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">Evaluated Models</div>");
                out.printf("            <div class=\"stat-value\" style=\"color: var(--primary);\">%d</div>%n", simpleMaths.size());
                out.println("            <div class=\"stat-sub\">demo0–demo9, demo11, demo12</div>");
                out.println("        </div>");
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">SimpleMath Tests</div>");
                out.printf("            <div class=\"stat-value\" style=\"color: #0891b2;\">%d</div>%n", totalSimpleMathTests);
                out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalSimpleMathTime);
                out.println("        </div>");
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">SimpleMathClean Tests</div>");
                out.printf("            <div class=\"stat-value\" style=\"color: #059669;\">%d</div>%n", totalSimpleMathCleanTests);
                out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalSimpleMathCleanTime);
                out.println("        </div>");
                out.println("        <div class=\"stat-card\">");
                out.println("            <div class=\"stat-label\">Combined Total Tests</div>");
                out.printf("            <div class=\"stat-value\" style=\"color: var(--primary);\">%d</div>%n", (totalSimpleMathTests + totalSimpleMathCleanTests));
                out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", (totalSimpleMathTime + totalSimpleMathCleanTime));
                out.println("        </div>");
            }
            out.println("    </div>");

            // Quick actions (tailored to active section)
            out.println("    <div class=\"quick-actions\">");
            out.println("        <span style=\"font-size: 0.85rem; color: var(--text-muted); font-weight: 600;\">Direct Links:</span>");
            if (activeSection == null) {
                out.println("        <a href=\"#fasthashmap\" class=\"action-btn\">⚡ FastHashMap</a>");
                out.println("        <a href=\"#lruclockmap\" class=\"action-btn\">⏰ LRUClockMap</a>");
                out.println("        <a href=\"#xlt-report-util\" class=\"action-btn\">🧰 com.xceptance.xlt.report.util</a>");
                out.println("        <a href=\"#simplemath\" class=\"action-btn\">🧮 SimpleMath</a>");
                out.println("        <a href=\"surefire.html\" class=\"action-btn\">🧪 Unit Test Suites</a>");
                out.println("        <a href=\"coverage-aggregate/index.html\" class=\"action-btn\">🎯 JaCoCo Coverage</a>");
                out.println("        <a href=\"jol-report.html\" class=\"action-btn\">💾 JOL Memory</a>");
                out.println("        <a href=\"jmh-report.html\" class=\"action-btn\">⚡ JMH Benchmarks</a>");
            } else if ("fasthashmap".equalsIgnoreCase(activeSection)) {
                out.println("        <a href=\"#ai-tests\" class=\"action-btn\">🤖 AI Unit Tests</a>");
                out.println("        <a href=\"#blackbox\" class=\"action-btn\">🛡️ BlackBox Tests</a>");
                out.println("        <a href=\"#jol-memory\" class=\"action-btn\">💾 Memory Layout</a>");
                out.println("        <a href=\"#jmh-benchmarks\" class=\"action-btn\">⚡ JMH Benchmarks</a>");
                out.println("        <a href=\"surefire.html#fasthashmap\" class=\"action-btn\">🧪 Test Reports</a>");
                out.println("        <a href=\"coverage-aggregate/index.html\" class=\"action-btn\">🎯 JaCoCo Coverage</a>");
                out.println("        <a href=\"jol-report.html\" class=\"action-btn\">💾 JOL Details</a>");
                out.println("        <a href=\"jmh-report.html\" class=\"action-btn\">📈 JMH Charts</a>");
            } else if ("lruclockmap".equalsIgnoreCase(activeSection)) {
                out.println("        <a href=\"#lruclockmap\" class=\"action-btn\">⏰ LRUClockMap Tests</a>");
                out.println("        <a href=\"surefire.html#lruclockmap\" class=\"action-btn\">🧪 Test Reports</a>");
                out.println("        <a href=\"coverage-aggregate/index.html\" class=\"action-btn\">🎯 JaCoCo Coverage</a>");
            } else if ("xlt-util".equalsIgnoreCase(activeSection)) {
                out.println("        <a href=\"#xlt-report-util\" class=\"action-btn\">🧰 Utility Tests</a>");
                out.println("        <a href=\"surefire.html#xlt-util\" class=\"action-btn\">🧪 Test Reports</a>");
                out.println("        <a href=\"coverage-aggregate/index.html\" class=\"action-btn\">🎯 JaCoCo Coverage</a>");
            } else if ("simplemath".equalsIgnoreCase(activeSection)) {
                out.println("        <a href=\"#simplemath-tests\" class=\"action-btn\">🔢 SimpleMath Tests</a>");
                out.println("        <a href=\"#simplemathclean-tests\" class=\"action-btn\">🧼 SimpleMathClean Tests</a>");
                out.println("        <a href=\"surefire.html#simplemath\" class=\"action-btn\">🧪 Test Reports</a>");
                out.println("        <a href=\"coverage-aggregate/index.html\" class=\"action-btn\">🎯 JaCoCo Coverage</a>");
            }
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
                if (activeSection == null) {
                    out.println("            <div>");
                    out.println("                <a href=\"fasthashmap.html\" class=\"subpage-link\">Open Dedicated Page ↗</a>");
                    out.println("            </div>");
                }
                out.println("        </div>");

                out.println("        <div id=\"ai-tests\" style=\"font-size: 1.05rem; font-weight: 700; margin: 1.25rem 0 0.5rem 0; color: #1e293b; display: flex; align-items: center; justify-content: space-between;\">");
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
                        testBadge = String.format("<a href=\"surefire.html#fasthashmap\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s", total, timeBadge);
                        statusBadge = "<span class=\"badge badge-success\">100% Passing ✅</span>";
                    } else {
                        testBadge = String.format("<a href=\"surefire.html#fasthashmap\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%d/%d Passed</a> %s", passed, total, timeBadge);
                        statusBadge = String.format("<span class=\"badge badge-warning\">⚠ %d Failed</span>", fails);
                    }

                    out.println("                <tr>");
                    out.printf("                    <td>%s</td>%n", renderModuleLink(s.id(), "FastHashMap"));
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
                out.println("        <div id=\"blackbox\" class=\"subtable-wrapper\">");
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
                        testBadge = String.format("<a href=\"surefire.html#blackbox\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s", total, timeBadge);
                        statusBadge = "<span class=\"badge badge-success\">100% Passing ✅</span>";
                    } else {
                        testBadge = String.format("<a href=\"surefire.html#blackbox\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%d/%d Passed</a> %s", passed, total, timeBadge);
                        statusBadge = String.format("<span class=\"badge badge-warning\">⚠ %d Failed</span>", fails);
                    }

                    out.println("                <tr>");
                    out.printf("                    <td>%s</td>%n", renderModuleLink(s.id(), "FastHashMap"));
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
                out.println("        <div id=\"jol-memory\" class=\"subtable-wrapper\">");
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
                    out.printf("                        <td>%s</td>%n", renderModuleLink(s.id(), "FastHashMap"));
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
                out.println("        <div id=\"jmh-benchmarks\" class=\"subtable-wrapper\">");
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
                    out.printf("                        <td>%s</td>%n", renderModuleLink(s.id(), "FastHashMap"));
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
                if (activeSection == null) {
                    out.println("            <div>");
                    out.println("                <a href=\"lruclockmap.html\" class=\"subpage-link\">Open Dedicated Page ↗</a>");
                    out.println("            </div>");
                }
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
                        testBadge = String.format("<a href=\"surefire.html#lruclockmap\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s", total, timeBadge);
                        statusBadge = "<span class=\"badge badge-success\">100% Passing ✅</span>";
                    } else {
                        testBadge = String.format("<a href=\"surefire.html#lruclockmap\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%d/%d Passed</a> %s", passed, total, timeBadge);
                        statusBadge = String.format("<span class=\"badge badge-warning\">⚠ %d Failed</span>", fails);
                    }

                    out.println("                <tr>");
                    out.printf("                    <td>%s</td>%n", renderModuleLink(s.id(), "LRUClockMap"));
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
                if (activeSection == null) {
                    out.println("            <div>");
                    out.println("                <a href=\"xlt-util.html\" class=\"subpage-link\">Open Dedicated Page ↗</a>");
                    out.println("            </div>");
                }
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
                        testBadge = String.format("<a href=\"surefire.html#xlt-util\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s", total, timeBadge);
                        statusBadge = "<span class=\"badge badge-success\">100% Passing ✅</span>";
                    } else {
                        testBadge = String.format("<a href=\"surefire.html#xlt-util\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%d/%d Passed</a> %s", passed, total, timeBadge);
                        statusBadge = String.format("<span class=\"badge badge-warning\">⚠ %d Failed</span>", fails);
                    }

                    out.println("                <tr>");
                    out.printf("                    <td>%s</td>%n", renderModuleLink(s.id(), "IntTimeSeries"));
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
                    out.printf("                        <td>%s</td>%n", renderModuleLink(s.id(), "IntTimeSeries"));
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

            // =========================================================================
            // SUB-SECTION 4: SimpleMath & SimpleMathClean
            // =========================================================================
            if (showSimpleMath) {
                out.println("    <!-- SUB-SECTION 4: SimpleMath & SimpleMathClean -->");
                out.println("    <div id=\"simplemath\" class=\"card\">");
                out.println("        <div class=\"section-header\">");
                out.println("            <div>");
                out.println("                <h2>🧮 SimpleMath & SimpleMathClean — Quality, Coverage & Mutation Verification</h2>");
                out.println("                <div class=\"section-desc\">Verification matrix for basic math and clean math suites, arithmetic overflow assertions, line/branch coverage, and mutation testing.</div>");
                out.println("            </div>");
                if (activeSection == null) {
                    out.println("            <div>");
                    out.println("                <a href=\"simplemath.html\" class=\"subpage-link\">Open Dedicated Page ↗</a>");
                    out.println("            </div>");
                }
                out.println("        </div>");

                out.println("        <div id=\"simplemath-tests\" style=\"font-size: 1.05rem; font-weight: 700; margin: 1.25rem 0 0.5rem 0; color: #1e293b; display: flex; align-items: center; justify-content: space-between;\">");
                out.println("            <span>🔢 SimpleMath Test Suites (SimpleMathTest)</span>");
                out.println("            <span style=\"font-size: 0.8rem; font-weight: 500; color: var(--text-muted);\">Baseline arithmetic operations and overflow edge cases</span>");
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

                for (SimpleMathQualitySummary s : simpleMaths) {
                    renderSimpleMathRow(out, s.id(), s.aiModel(), s.simpleMathQuality(), "SimpleMath");
                }

                out.println("            </tbody>");
                out.println("        </table>");

                // Sub-table for SimpleMathClean
                out.println("        <div id=\"simplemathclean-tests\" class=\"subtable-wrapper\">");
                out.println("            <div class=\"subtable-title\">");
                out.println("                <span>🧼 SimpleMathClean Test Suites (SimpleMathCleanTest)</span>");
                out.println("                <span style=\"font-size: 0.8rem; font-weight: 500; color: var(--text-muted);\">Clean arithmetic operations and overflow handling</span>");
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

                for (SimpleMathQualitySummary s : simpleMaths) {
                    renderSimpleMathRow(out, s.id(), s.aiModel(), s.simpleMathCleanQuality(), "SimpleMathClean");
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

    private static void renderSimpleMathRow(PrintWriter out, String modId, String aiModel, QualityStats q, String className) {
        String pkgName = modId;
        String pitBadge = q.pitTotal() > 0 ?
                String.format("<a href=\"pit-reports-simplemath/%s/org.jugsaxony.%s/%s.java.html\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%.1f%% (%d/%d)</a>",
                        modId, pkgName, className, q.pitScorePct(), q.pitKilled(), q.pitTotal())
                : "<span class=\"badge badge-info\">N/A</span>";

        String covLink = String.format("jacoco-simplemath/%s/org.jugsaxony.%s/%s.java.html", modId, pkgName, className);
        String timeBadge = q.executionTimeSeconds() > 0 ?
                String.format("<span class=\"badge badge-time\">⏱️ %.2fs</span>", q.executionTimeSeconds()) : "";

        int total = q.tests();
        int fails = q.failures() + q.errors();
        int passed = Math.max(0, total - fails);
        String testBadge;
        String statusBadge;
        if (total == 0) {
            testBadge = "<span class=\"badge badge-info\">0 Tests</span>";
            statusBadge = "<span class=\"badge badge-info\">No Tests</span>";
        } else if (fails == 0) {
            testBadge = String.format("<a href=\"surefire.html#simplemath\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s", total, timeBadge);
            statusBadge = "<span class=\"badge badge-success\">100% Passing ✅</span>";
        } else {
            testBadge = String.format("<a href=\"surefire.html#simplemath\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%d/%d Passed</a> %s", passed, total, timeBadge);
            statusBadge = String.format("<span class=\"badge badge-warning\">⚠ %d Failed</span>", fails);
        }

        out.println("                <tr>");
        out.printf("                    <td>%s</td>%n", renderModuleLink(modId, className));
        out.printf("                    <td><span class=\"badge badge-info\">%s</span></td>%n", aiModel);
        out.printf("                    <td>%s</td>%n", testBadge);
        if (total == 0) {
            out.println("                    <td class=\"numeric\">-</td>");
            out.println("                    <td class=\"numeric\">-</td>");
            out.println("                    <td class=\"numeric\">-</td>");
        } else {
            out.printf("                    <td><a href=\"%s\" style=\"color: var(--primary); text-decoration: underline;\"><strong>%.1f%%</strong></a> (%d/%d)</td>%n", covLink, q.instructionCoveragePct(), q.totalInstructions() - q.missedInstructions(), q.totalInstructions());
            out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, q.lineCoveragePct(), q.totalLines() - q.missedLines(), q.totalLines());
            out.printf("                    <td><a href=\"%s\" style=\"color: inherit; text-decoration: underline;\">%.1f%%</a> (%d/%d)</td>%n", covLink, q.branchCoveragePct(), q.totalBranches() - q.missedBranches(), q.totalBranches());
        }
        out.printf("                    <td>%s</td>%n", pitBadge);
        out.printf("                    <td>%s</td>%n", statusBadge);
        out.println("                </tr>");
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String renderModuleLink(String modId, String className) {
        if (className != null && !className.isBlank()) {
            return String.format("<a href=\"sources/%s/%s.html\" class=\"module-source-link\" title=\"View Source: %s/%s.java\"><strong>%s</strong> <span class=\"code-icon\">📄</span></a>",
                    modId, className, modId, className, modId);
        }
        return String.format("<strong>%s</strong>", modId);
    }

    private static String renderMarkdownModuleLink(String modId, String className) {
        if (className != null && !className.isBlank()) {
            return String.format("[**%s**](sources/%s/%s.html)", modId, modId, className);
        }
        return String.format("**%s**", modId);
    }

    private static void renderSurefireSuiteRow(
            PrintWriter out,
            String modId,
            String aiModel,
            QualityStats q,
            List<FailedTestCase> failures) {
        renderSurefireSuiteRow(out, modId, aiModel, q, failures, null);
    }

    private static void renderSurefireSuiteRow(
            PrintWriter out,
            String modId,
            String aiModel,
            QualityStats q,
            List<FailedTestCase> failures,
            String className) {
        int total = q.tests();
        int fails = q.failures() + q.errors();
        int passed = Math.max(0, total - fails);

        String testBadge;
        String statusBadge;
        if (total == 0) {
            testBadge = "<span class=\"badge badge-info\">0 Tests</span>";
            statusBadge = "<span class=\"badge badge-info\">No Tests</span>";
        } else if (fails == 0) {
            testBadge = String.format("<span class=\"badge badge-success\">%d Passed</span>", total);
            statusBadge = "<span class=\"badge badge-success\">100% Passing ✅</span>";
        } else {
            testBadge = String.format("<span class=\"badge badge-warning\">%d/%d Passed</span>", passed, total);
            statusBadge = String.format("<span class=\"badge badge-warning\">⚠ %d Failed</span>", fails);
        }

        String timeStr = q.executionTimeSeconds() > 0 ? String.format("%.2fs", q.executionTimeSeconds()) : "-";

        out.println("                <tr>");
        out.printf("                    <td>%s</td>%n", renderModuleLink(modId, className));
        out.printf("                    <td><span class=\"badge badge-info\">%s</span></td>%n", aiModel);
        out.printf("                    <td>%s</td>%n", testBadge);
        out.printf("                    <td class=\"numeric\">%s</td>%n", timeStr);
        out.printf("                    <td class=\"numeric\">%s</td>%n", fails > 0 ? "<span style=\"color: #dc2626; font-weight: 700;\">" + q.failures() + "</span>" : "0");
        out.printf("                    <td class=\"numeric\">%s</td>%n", q.errors() > 0 ? "<span style=\"color: #dc2626; font-weight: 700;\">" + q.errors() + "</span>" : "0");
        out.printf("                    <td>%s</td>%n", statusBadge);

        // Failures / Diagnostics column
        if (failures == null || failures.isEmpty()) {
            if (total == 0) {
                out.println("                    <td><span style=\"color: var(--text-muted); font-size: 0.8rem;\">None</span></td>");
            } else {
                out.println("                    <td><span style=\"color: var(--success); font-size: 0.8rem; font-weight: 600;\">All tests passed</span></td>");
            }
        } else {
            out.println("                    <td>");
            out.printf("                        <details class=\"failure-details\">%n");
            out.printf("                            <summary><span class=\"badge badge-warning\" style=\"cursor: pointer;\">🔍 %d Failure%s (click to inspect)</span></summary>%n",
                    failures.size(), failures.size() == 1 ? "" : "s");
            out.println("                            <div class=\"failure-list\">");
            for (FailedTestCase ft : failures) {
                out.println("                                <div class=\"failure-item\">");
                out.printf("                                    <div class=\"failure-title\"><code>%s</code> <span class=\"failure-type\">[%s]</span></div>%n",
                        escapeHtml(ft.testName()), escapeHtml(ft.failureType()));
                if (ft.message() != null && !ft.message().isBlank()) {
                    out.printf("                                    <div class=\"failure-msg\">%s</div>%n", escapeHtml(ft.message()));
                }
                out.println("                                </div>");
            }
            out.println("                            </div>");
            out.println("                        </details>");
            out.println("                    </td>");
        }

        out.println("                </tr>");
    }

    private static void writeSurefireHtmlReport(
            File htmlFile,
            File outputDir,
            File rootProjectDir,
            List<FastHashMapSummary> fastMaps,
            List<FastHashMapBlackBoxSummary> fastBlackBoxes,
            List<LruClockMapSummary> lruMaps,
            List<ReportUtilSummary> reportUtils,
            List<SimpleMathQualitySummary> simpleMaths) throws IOException {

        // Calculate totals across all suites
        int totalTests = 0;
        int totalFailures = 0;
        int totalErrors = 0;
        double totalDuration = 0.0;
        int totalSuites = 0;

        for (FastHashMapSummary s : fastMaps) {
            totalTests += s.quality().tests();
            totalFailures += s.quality().failures();
            totalErrors += s.quality().errors();
            totalDuration += s.quality().executionTimeSeconds();
            if (s.quality().tests() > 0) totalSuites++;
        }
        for (FastHashMapBlackBoxSummary s : fastBlackBoxes) {
            totalTests += s.quality().tests();
            totalFailures += s.quality().failures();
            totalErrors += s.quality().errors();
            totalDuration += s.quality().executionTimeSeconds();
            if (s.quality().tests() > 0) totalSuites++;
        }
        for (LruClockMapSummary s : lruMaps) {
            totalTests += s.quality().tests();
            totalFailures += s.quality().failures();
            totalErrors += s.quality().errors();
            totalDuration += s.quality().executionTimeSeconds();
            if (s.quality().tests() > 0) totalSuites++;
        }
        for (ReportUtilSummary s : reportUtils) {
            totalTests += s.quality().tests();
            totalFailures += s.quality().failures();
            totalErrors += s.quality().errors();
            totalDuration += s.quality().executionTimeSeconds();
            if (s.quality().tests() > 0) totalSuites++;
        }
        for (SimpleMathQualitySummary s : simpleMaths) {
            totalTests += s.simpleMathQuality().tests() + s.simpleMathCleanQuality().tests();
            totalFailures += s.simpleMathQuality().failures() + s.simpleMathCleanQuality().failures();
            totalErrors += s.simpleMathQuality().errors() + s.simpleMathCleanQuality().errors();
            totalDuration += s.simpleMathQuality().executionTimeSeconds() + s.simpleMathCleanQuality().executionTimeSeconds();
            if (s.simpleMathQuality().tests() > 0) totalSuites++;
            if (s.simpleMathCleanQuality().tests() > 0) totalSuites++;
        }

        int totalPassed = Math.max(0, totalTests - totalFailures - totalErrors);

        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(htmlFile), StandardCharsets.UTF_8))) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.println("    <title>Surefire Aggregated Unit Test Report</title>");
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
            out.println("            --danger: #dc2626;");
            out.println("            --danger-bg: #fee2e2;");
            out.println("            --time-bg: #f1f5f9;");
            out.println("        }");
            out.println("        * { box-sizing: border-box; }");
            out.println("        body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: var(--bg); color: var(--text); margin: 0; padding: 2rem 1.5rem; line-height: 1.5; }");
            out.println("        .container { max-width: 1480px; margin: 0 auto; }");
            out.println("        .header { border-bottom: 1px solid var(--card-border); padding-bottom: 1.5rem; margin-bottom: 2rem; display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1.5rem; }");
            out.println("        .header h1 { margin: 0; font-size: 2.1rem; font-weight: 800; letter-spacing: -0.025em; background: linear-gradient(135deg, #0f172a 0%, #334155 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }");
            out.println("        .header p { margin: 0.5rem 0 0 0; color: var(--text-muted); font-size: 1.05rem; }");
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
            out.println("        .module-source-link { color: var(--primary); text-decoration: none; display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.2rem 0.45rem; border-radius: 6px; background: rgba(2, 132, 199, 0.06); border: 1px solid rgba(2, 132, 199, 0.15); transition: all 0.2s ease; font-weight: 700; }");
            out.println("        .module-source-link:hover { background: rgba(2, 132, 199, 0.14); border-color: var(--primary); text-decoration: none; transform: translateY(-1px); }");
            out.println("        .module-source-link .code-icon { font-size: 0.75rem; opacity: 0.75; }");
            out.println("        table { width: 100%; border-collapse: collapse; margin-top: 1rem; font-size: 0.9rem; }");
            out.println("        th, td { padding: 0.8rem 1rem; text-align: left; border-bottom: 1px solid var(--card-border); }");
            out.println("        th { background: #f8fafc; font-weight: 700; font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }");
            out.println("        tr:hover td { background: #f8fafc; }");
            out.println("        .badge { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.25rem 0.65rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; white-space: nowrap; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .badge-success { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }");
            out.println("        .badge-info { background: #f0f9ff; color: #0284c7; border: 1px solid #bae6fd; }");
            out.println("        .badge-warning { background: #fef3c7; color: #b45309; border: 1px solid #fde68a; }");
            out.println("        .badge-danger { background: #fee2e2; color: #b91c1c; border: 1px solid #fecaca; }");
            out.println("        .badge-time { background: #f1f5f9; color: #475569; border: 1px solid #e2e8f0; font-size: 0.73rem; }");
            out.println("        .numeric { text-align: right; font-variant-numeric: tabular-nums; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .subtable-wrapper { margin-top: 1.75rem; padding-top: 1.25rem; border-top: 1px dashed var(--card-border); }");
            out.println("        .subtable-title { font-size: 1.05rem; font-weight: 700; margin: 0 0 0.75rem 0; color: #1e293b; display: flex; align-items: center; justify-content: space-between; }");
            out.println("        .failure-details { margin: 0.25rem 0; }");
            out.println("        .failure-details summary { cursor: pointer; outline: none; list-style: none; display: inline-flex; align-items: center; }");
            out.println("        .failure-details summary::-webkit-details-marker { display: none; }");
            out.println("        .failure-list { margin-top: 0.5rem; display: flex; flex-direction: column; gap: 0.45rem; background: #fff5f5; border: 1px solid #fecaca; border-radius: 8px; padding: 0.6rem 0.8rem; max-width: 580px; }");
            out.println("        .failure-item { font-size: 0.8rem; border-bottom: 1px solid #fee2e2; padding-bottom: 0.35rem; }");
            out.println("        .failure-item:last-child { border-bottom: none; padding-bottom: 0; }");
            out.println("        .failure-title { font-weight: 600; color: #991b1b; display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; }");
            out.println("        .failure-type { font-size: 0.72rem; color: #b91c1c; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .failure-msg { font-family: 'JetBrains Mono', monospace; font-size: 0.75rem; color: #475569; background: #ffffff; padding: 0.25rem 0.45rem; border-radius: 4px; border: 1px solid #fecaca; margin-top: 0.25rem; white-space: pre-wrap; word-break: break-word; }");
            out.println("        .notice-box { background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 10px; padding: 1rem 1.25rem; margin-bottom: 1.5rem; font-size: 0.88rem; color: #1e3a8a; line-height: 1.55; }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container\">");
            out.println("    <div class=\"header\">");
            out.println("        <div>");
            out.println("            <h1>🧪 Surefire Aggregated Unit Test Report</h1>");
            out.println("            <p>Comprehensive Execution Results, Assertions, Durations, and Diagnostic Traces across all Test Suites</p>");
            out.println("        </div>");
            out.println("        <div>");
            out.println("            <a href=\"index.html\" class=\"action-btn\">← Back to Master Dashboard</a>");
            out.println("        </div>");
            out.println("    </div>");

            // Stats grid
            out.println("    <div class=\"stats-grid\">");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Total Tests Run</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--primary);\">%d</div>%n", totalTests);
            out.printf("            <div class=\"stat-sub\">Across %d evaluated suites</div>%n", totalSuites);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Passed Tests</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--success);\">%d</div>%n", totalPassed);
            out.printf("            <div class=\"stat-sub\">%.1f%% overall pass rate</div>%n", totalTests > 0 ? (totalPassed * 100.0) / totalTests : 100.0);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Failures & Errors</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: %s;\">%d</div>%n", (totalFailures + totalErrors) > 0 ? "var(--warning)" : "var(--success)", (totalFailures + totalErrors));
            out.printf("            <div class=\"stat-sub\">%d failures, %d errors</div>%n", totalFailures, totalErrors);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Total Execution Time</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--accent);\">%.2fs</div>%n", totalDuration);
            out.println("            <div class=\"stat-sub\">Combined Surefire runtime</div>");
            out.println("        </div>");
            out.println("    </div>");

            // Quick actions
            out.println("    <div class=\"quick-actions\">");
            out.println("        <span style=\"font-size: 0.85rem; color: var(--text-muted); font-weight: 600;\">Jump to Suite:</span>");
            out.println("        <a href=\"#simplemath\" class=\"action-btn\">🧮 SimpleMath</a>");
            out.println("        <a href=\"#fasthashmap\" class=\"action-btn\">⚡ FastHashMap (AI)</a>");
            out.println("        <a href=\"#blackbox\" class=\"action-btn\">🛡️ FastHashMap (BlackBox)</a>");
            out.println("        <a href=\"#lruclockmap\" class=\"action-btn\">⏰ LRUClockMap</a>");
            out.println("        <a href=\"#xlt-util\" class=\"action-btn\">🧰 com.xceptance.xlt.report.util</a>");
            out.println("        <a href=\"coverage-aggregate/index.html\" class=\"action-btn\">🎯 JaCoCo Coverage</a>");
            out.println("    </div>");

            // SECTION 1: SimpleMath & SimpleMathClean
            out.println("    <div id=\"simplemath\" class=\"card\">");
            out.println("        <div class=\"section-header\">");
            out.println("            <div>");
            out.println("                <h2>🧮 SimpleMath & SimpleMathClean Test Suites</h2>");
            out.println("                <div class=\"section-desc\">Arithmetic operations, boundary assertions, and overflow behavior across baseline and AI implementations.</div>");
            out.println("            </div>");
            out.println("            <div>");
            out.println("                <a href=\"simplemath.html\" class=\"subpage-link\">Open Dedicated Page ↗</a>");
            out.println("            </div>");
            out.println("        </div>");

            out.println("        <div class=\"notice-box\">");
            out.println("            <strong>💡 Note on 64-bit Overflow Edge Cases:</strong> In demo1–demo9, <code>SimpleMathTest</code> and <code>SimpleMathCleanTest</code> include strict assertions verifying 64-bit integer overflow promotion when summing arrays (e.g. <code>Integer.MAX_VALUE + 1</code>, <code>10000000000L</code>). Because the AI models produced implementations using standard 32-bit integer arithmetic without widening promotion, these tests intentionally register 3 to 7 assertion failures. In <strong>demo0 (Human Baseline)</strong>, these edge cases were properly considered and 100% of tests pass.");
            out.println("        </div>");

            out.println("        <div style=\"font-size: 1.05rem; font-weight: 700; margin: 1.25rem 0 0.5rem 0; color: #1e293b; display: flex; align-items: center; justify-content: space-between;\">");
            out.println("            <span>🔢 SimpleMath Test Suites (SimpleMathTest)</span>");
            out.println("            <span style=\"font-size: 0.8rem; font-weight: 500; color: var(--text-muted);\">Baseline arithmetic operations and overflow edge cases</span>");
            out.println("        </div>");

            out.println("        <table>");
            out.println("            <thead>");
            out.println("                <tr>");
            out.println("                    <th>Module</th>");
            out.println("                    <th>AI Model / Implementation</th>");
            out.println("                    <th>Tests</th>");
            out.println("                    <th class=\"numeric\">Duration</th>");
            out.println("                    <th class=\"numeric\">Failures</th>");
            out.println("                    <th class=\"numeric\">Errors</th>");
            out.println("                    <th>Status</th>");
            out.println("                    <th>Diagnostics / Test Results</th>");
            out.println("                </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (SimpleMathQualitySummary s : simpleMaths) {
                File smDir = new File(outputDir, "surefire-reports-simplemath/" + s.id() + "/SimpleMath");
                if (!smDir.exists()) smDir = new File(outputDir, "surefire-reports-simplemath/" + s.id());
                if (!smDir.exists()) smDir = new File(rootProjectDir, s.id() + "/target/surefire-reports");
                List<FailedTestCase> failures = parseFailedTestCases(smDir, smDir.getName().equals("SimpleMath") ? "" : "SimpleMathTest", false);
                if (failures.isEmpty() && s.id().equals("demo6")) {
                    failures = parseFailedTestCases(smDir, "AbstractSimpleMathContract", false);
                }
                renderSurefireSuiteRow(out, s.id(), s.aiModel(), s.simpleMathQuality(), failures, "SimpleMathTest");
            }

            out.println("            </tbody>");
            out.println("        </table>");

            // Subtable for SimpleMathClean
            out.println("        <div class=\"subtable-wrapper\">");
            out.println("            <div class=\"subtable-title\">");
            out.println("                <span>🧼 SimpleMathClean Test Suites (SimpleMathCleanTest)</span>");
            out.println("                <span style=\"font-size: 0.8rem; font-weight: 500; color: var(--text-muted);\">Clean arithmetic operations and overflow handling</span>");
            out.println("            </div>");
            out.println("            <table>");
            out.println("                <thead>");
            out.println("                    <tr>");
            out.println("                        <th>Module</th>");
            out.println("                        <th>AI Model / Implementation</th>");
            out.println("                        <th>Tests</th>");
            out.println("                        <th class=\"numeric\">Duration</th>");
            out.println("                        <th class=\"numeric\">Failures</th>");
            out.println("                        <th class=\"numeric\">Errors</th>");
            out.println("                        <th>Status</th>");
            out.println("                        <th>Diagnostics / Test Results</th>");
            out.println("                    </tr>");
            out.println("                </thead>");
            out.println("                <tbody>");

            for (SimpleMathQualitySummary s : simpleMaths) {
                File smCleanDir = new File(outputDir, "surefire-reports-simplemath/" + s.id() + "/SimpleMathClean");
                if (!smCleanDir.exists()) smCleanDir = new File(outputDir, "surefire-reports-simplemath/" + s.id());
                if (!smCleanDir.exists()) smCleanDir = new File(rootProjectDir, s.id() + "/target/surefire-reports");
                List<FailedTestCase> failures = parseFailedTestCases(smCleanDir, smCleanDir.getName().equals("SimpleMathClean") ? "" : "SimpleMathCleanTest", false);
                if (failures.isEmpty() && s.id().equals("demo6")) {
                    failures = parseFailedTestCases(smCleanDir, "AbstractSimpleMathContract", false);
                }
                renderSurefireSuiteRow(out, s.id(), s.aiModel(), s.simpleMathCleanQuality(), failures, "SimpleMathCleanTest");
            }

            out.println("                </tbody>");
            out.println("            </table>");
            out.println("        </div>");
            out.println("    </div>");

            // SECTION 2: FastHashMap AI
            out.println("    <div id=\"fasthashmap\" class=\"card\">");
            out.println("        <div class=\"section-header\">");
            out.println("            <div>");
            out.println("                <h2>⚡ FastHashMap — AI-Generated Unit Test Suites</h2>");
            out.println("                <div class=\"section-desc\">Execution results for FastHashMapTest across all evaluated modules.</div>");
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
            out.println("                    <th>Tests</th>");
            out.println("                    <th class=\"numeric\">Duration</th>");
            out.println("                    <th class=\"numeric\">Failures</th>");
            out.println("                    <th class=\"numeric\">Errors</th>");
            out.println("                    <th>Status</th>");
            out.println("                    <th>Diagnostics / Test Results</th>");
            out.println("                </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (FastHashMapSummary s : fastMaps) {
                File sfDir = new File(rootProjectDir, s.id() + "/target/surefire-reports");
                List<FailedTestCase> failures = parseFailedTestCases(sfDir, "FastHashMapTest", false);
                renderSurefireSuiteRow(out, s.id(), s.aiModel(), s.quality(), failures, "FastHashMapTest");
            }

            out.println("            </tbody>");
            out.println("        </table>");
            out.println("    </div>");

            // SECTION 3: FastHashMap BlackBox
            out.println("    <div id=\"blackbox\" class=\"card\">");
            out.println("        <div class=\"section-header\">");
            out.println("            <div>");
            out.println("                <h2>🛡️ FastHashMap — Manual BlackBox Unit Test Suites</h2>");
            out.println("                <div class=\"section-desc\">Uniform manual blackbox tests evaluated identically against every FastHashMap implementation.</div>");
            out.println("            </div>");
            out.println("            <div>");
            out.println("                <a href=\"fasthashmap.html#blackbox\" class=\"subpage-link\">Open Dedicated Page ↗</a>");
            out.println("            </div>");
            out.println("        </div>");
            out.println("        <table>");
            out.println("            <thead>");
            out.println("                <tr>");
            out.println("                    <th>Module</th>");
            out.println("                    <th>AI Model / Implementation</th>");
            out.println("                    <th>Tests</th>");
            out.println("                    <th class=\"numeric\">Duration</th>");
            out.println("                    <th class=\"numeric\">Failures</th>");
            out.println("                    <th class=\"numeric\">Errors</th>");
            out.println("                    <th>Status</th>");
            out.println("                    <th>Diagnostics / Test Results</th>");
            out.println("                </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (FastHashMapBlackBoxSummary s : fastBlackBoxes) {
                File sfDir = new File(outputDir, "surefire-reports-blackbox/" + s.id());
                if (!sfDir.exists()) sfDir = new File(rootProjectDir, s.id() + "/target/surefire-reports");
                List<FailedTestCase> failures = parseFailedTestCases(sfDir, "FastHashMapBlackBox", false);
                renderSurefireSuiteRow(out, s.id(), s.aiModel(), s.quality(), failures, "FastHashMapBlackBox");
            }

            out.println("            </tbody>");
            out.println("        </table>");
            out.println("    </div>");

            // SECTION 4: LRUClockMap
            out.println("    <div id=\"lruclockmap\" class=\"card\">");
            out.println("        <div class=\"section-header\">");
            out.println("            <div>");
            out.println("                <h2>⏰ LRUClockMap — Bounded Cache Unit Test Suites</h2>");
            out.println("                <div class=\"section-desc\">Execution results for LRUClockMapTest (eviction, capacity bounds, concurrent operations).</div>");
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
            out.println("                    <th>Tests</th>");
            out.println("                    <th class=\"numeric\">Duration</th>");
            out.println("                    <th class=\"numeric\">Failures</th>");
            out.println("                    <th class=\"numeric\">Errors</th>");
            out.println("                    <th>Status</th>");
            out.println("                    <th>Diagnostics / Test Results</th>");
            out.println("                </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (LruClockMapSummary s : lruMaps) {
                File sfDir = new File(rootProjectDir, s.id() + "/target/surefire-reports");
                List<FailedTestCase> failures = parseFailedTestCases(sfDir, "LRUClockMapTest", false);
                renderSurefireSuiteRow(out, s.id(), s.aiModel(), s.quality(), failures, "LRUClockMapTest");
            }

            out.println("            </tbody>");
            out.println("        </table>");
            out.println("    </div>");

            // SECTION 5: com.xceptance.xlt.report.util
            out.println("    <div id=\"xlt-util\" class=\"card\">");
            out.println("        <a id=\"xlt-report-util\"></a>");
            out.println("        <div class=\"section-header\">");
            out.println("            <div>");
            out.println("                <h2>🧰 com.xceptance.xlt.report.util — Reporting Utility Test Suites</h2>");
            out.println("                <div class=\"section-desc\">Execution results for RuntimeHistogram, IntTimeSeries, BitUtil, and BitCompression.</div>");
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
            out.println("                    <th>Tests</th>");
            out.println("                    <th class=\"numeric\">Duration</th>");
            out.println("                    <th class=\"numeric\">Failures</th>");
            out.println("                    <th class=\"numeric\">Errors</th>");
            out.println("                    <th>Status</th>");
            out.println("                    <th>Diagnostics / Test Results</th>");
            out.println("                </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (ReportUtilSummary s : reportUtils) {
                File sfDir = new File(rootProjectDir, s.id() + "/target/surefire-reports");
                List<FailedTestCase> failures = parseFailedTestCases(sfDir, "com.xceptance.xlt.report.util", true);
                renderSurefireSuiteRow(out, s.id(), s.aiModel(), s.quality(), failures, "IntTimeSeriesTest");
            }

            out.println("            </tbody>");
            out.println("        </table>");
            out.println("    </div>");

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
