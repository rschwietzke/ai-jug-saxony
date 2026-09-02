package org.jugsaxony.report;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlobalJmhReportGenerator {

    public record BenchmarkEntry(
            String fullBenchmarkName,
            String operation,
            String targetId,
            String modelName,
            double score,
            double scoreError,
            String unit
    ) {}

    public static final Map<String, String> MODEL_NAMES = Map.of(
            "demo0", "Demo 0 (Baseline / FastRandom)",
            "demo1", "Demo 1 (Gemini 3.7 Flash High / Antigravity)",
            "demo2", "Demo 2 (Kimi K3)",
            "demo3", "Demo 3 (OpenAI 5.6 Sol Max)",
            "demo4", "Demo 4 (Gemma 4 31B Thinking)",
            "demo5", "Demo 5 (Deepseek V4 Flash Max)",
            "demo6", "Demo 6 (Claude Opus 5 Ultra)",
            "demo7", "Demo 7 (Qwen 38 max XHigh)",
            "demo8", "Demo 8 (Gemini 3.7 Flash High / Kilo Code)",
            "javaMap", "java.util.HashMap (JDK Baseline)"
    );

    public static List<BenchmarkEntry> parseJmhJson(File jsonFile) throws IOException {
        String content = Files.readString(jsonFile.toPath());
        List<BenchmarkEntry> entries = new ArrayList<>();

        // Split top-level JSON array into items
        // Each entry starts with "jmhVersion" or "benchmark"
        Pattern entryPattern = Pattern.compile("\\{\\s*\"jmhVersion\".*?\"secondaryMetrics\"\\s*:\\s*\\{\\s*\\}\\s*\\}", Pattern.DOTALL);
        Matcher entryMatcher = entryPattern.matcher(content);

        Pattern benchmarkPat = Pattern.compile("\"benchmark\"\\s*:\\s*\"([^\"]+)\"");
        Pattern scorePat = Pattern.compile("\"score\"\\s*:\\s*([0-9.Ee+-]+)");
        Pattern errorPat = Pattern.compile("\"scoreError\"\\s*:\\s*(\"NaN\"|[0-9.Ee+-]+)");
        Pattern unitPat = Pattern.compile("\"scoreUnit\"\\s*:\\s*\"([^\"]+)\"");

        while (entryMatcher.find()) {
            String block = entryMatcher.group(0);

            Matcher bm = benchmarkPat.matcher(block);
            Matcher sm = scorePat.matcher(block);
            Matcher em = errorPat.matcher(block);
            Matcher um = unitPat.matcher(block);

            if (bm.find() && sm.find()) {
                String benchmark = bm.group(1);
                double score = Double.parseDouble(sm.group(1));
                double scoreError = 0.0;
                if (em.find()) {
                    String errVal = em.group(1);
                    if (!errVal.contains("NaN") && !errVal.equals("\"NaN\"")) {
                        scoreError = Double.parseDouble(errVal.replace("\"", ""));
                    }
                }
                String unit = um.find() ? um.group(1) : "ops/us";

                String methodName = benchmark.substring(benchmark.lastIndexOf('.') + 1);
                String operation = "unknown";
                String targetId = "unknown";

                if (methodName.contains("_")) {
                    String[] parts = methodName.split("_", 2);
                    operation = parts[0];
                    targetId = parts[1];
                }

                String modelName = MODEL_NAMES.getOrDefault(targetId, targetId);
                entries.add(new BenchmarkEntry(benchmark, operation, targetId, modelName, score, scoreError, unit));
            }
        }

        return entries;
    }

    public static void generateReports(File jsonFile, File outputDir) throws IOException {
        if (!jsonFile.exists()) {
            System.err.println("JMH JSON file not found: " + jsonFile.getAbsolutePath());
            return;
        }

        List<BenchmarkEntry> entries = parseJmhJson(jsonFile);
        if (entries.isEmpty()) {
            System.err.println("No benchmark entries parsed from: " + jsonFile.getAbsolutePath());
            return;
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        generateMarkdownReport(new File(outputDir, "jmh-report.md"), entries);
        generateHtmlReport(new File(outputDir, "jmh-report.html"), entries);
        generateCsvReport(new File(outputDir, "jmh-report.csv"), entries);
    }

    public static void generateMarkdownReport(File targetFile, List<BenchmarkEntry> entries) throws IOException {
        Map<String, List<BenchmarkEntry>> byOp = new LinkedHashMap<>();
        for (BenchmarkEntry e : entries) {
            byOp.computeIfAbsent(e.operation(), k -> new ArrayList<>()).add(e);
        }

        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("# JMH Microbenchmark Cross-Project Comparison Report");
            out.println();
            out.println("Throughput benchmark results comparing all AI model implementations against `java.util.HashMap` (Size = 1,000 items).");
            out.println();

            for (Map.Entry<String, List<BenchmarkEntry>> group : byOp.entrySet()) {
                String op = group.getKey();
                List<BenchmarkEntry> list = new ArrayList<>(group.getValue());
                list.sort((a, b) -> Double.compare(b.score(), a.score())); // Highest throughput first

                double baselineScore = list.stream()
                        .filter(e -> "javaMap".equals(e.targetId()))
                        .mapToDouble(BenchmarkEntry::score)
                        .findFirst()
                        .orElse(1.0);

                out.println("## Operation: `" + op + "`");
                out.println();
                out.println("| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup vs HashMap |");
                out.println("| :--- | :--- | :--- | :--- | :--- | :--- |");

                int rank = 1;
                for (BenchmarkEntry e : list) {
                    double speedup = e.score() / baselineScore;
                    String speedupStr = String.format("%.2fx", speedup);
                    if (speedup >= 1.05) {
                        speedupStr = "**" + speedupStr + " 🚀**";
                    } else if (speedup <= 0.95) {
                        speedupStr = speedupStr + " 🔻";
                    }

                    out.printf("| %d | **%s** | %s | %,.2f | ± %,.2f | %s |%n",
                            rank++,
                            e.targetId(),
                            e.modelName(),
                            e.score(),
                            e.scoreError(),
                            speedupStr
                    );
                }
                out.println();
            }
        }
    }

    public static void generateCsvReport(File targetFile, List<BenchmarkEntry> entries) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("Operation,TargetId,ModelName,Score_OpsPerMicrosec,ScoreError,Unit");
            for (BenchmarkEntry e : entries) {
                out.printf("\"%s\",\"%s\",\"%s\",%.4f,%.4f,\"%s\"%n",
                        e.operation(), e.targetId(), e.modelName(), e.score(), e.scoreError(), e.unit());
            }
        }
    }

    public static void generateHtmlReport(File targetFile, List<BenchmarkEntry> entries) throws IOException {
        Map<String, List<BenchmarkEntry>> byOp = new LinkedHashMap<>();
        for (BenchmarkEntry e : entries) {
            byOp.computeIfAbsent(e.operation(), k -> new ArrayList<>()).add(e);
        }

        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.println("    <title>JMH Cross-Project Performance Report - AI JUG Saxony</title>");
            out.println("    <style>");
            out.println("        :root { --bg: #f8fafc; --card-bg: #ffffff; --text: #0f172a; --text-muted: #64748b; --border: #e2e8f0; --primary: #3b82f6; --success: #10b981; --accent: #8b5cf6; }");
            out.println("        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: var(--bg); color: var(--text); margin: 0; padding: 2rem; }");
            out.println("        .container { max-width: 1200px; margin: 0 auto; }");
            out.println("        .header { margin-bottom: 2rem; padding-bottom: 1rem; border-bottom: 2px solid var(--border); }");
            out.println("        .header h1 { margin: 0 0 0.5rem 0; font-size: 2rem; color: #1e293b; }");
            out.println("        .header p { margin: 0; color: var(--text-muted); font-size: 1.1rem; }");
            out.println("        .card { background: var(--card-bg); border-radius: 10px; border: 1px solid var(--border); padding: 1.5rem; margin-bottom: 2rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            out.println("        h2 { font-size: 1.4rem; margin-top: 0; margin-bottom: 1rem; color: #1e293b; }");
            out.println("        table { width: 100%; border-collapse: collapse; margin-top: 1rem; }");
            out.println("        th, td { padding: 0.75rem 1rem; text-align: left; border-bottom: 1px solid var(--border); }");
            out.println("        th { background: #f1f5f9; font-weight: 600; font-size: 0.875rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }");
            out.println("        tr:hover { background: #f8fafc; }");
            out.println("        .badge { display: inline-block; padding: 0.25rem 0.5rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; }");
            out.println("        .badge-winner { background: #dcfce7; color: #15803d; border: 1px solid #86efac; }");
            out.println("        .badge-primary { background: #dbeafe; color: #1d4ed8; }");
            out.println("        .numeric { text-align: right; font-variant-numeric: tabular-nums; }");
            out.println("        .bar-container { background: #f1f5f9; border-radius: 4px; width: 120px; height: 12px; display: inline-block; margin-right: 8px; vertical-align: middle; overflow: hidden; }");
            out.println("        .bar-fill { background: #3b82f6; height: 100%; }");
            out.println("        .speedup-fast { color: #16a34a; font-weight: 700; }");
            out.println("        .speedup-slow { color: #dc2626; }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container\">");
            out.println("    <div class=\"header\">");
            out.println("        <h1>⚡ JMH Cross-Project Performance Report</h1>");
            out.println("        <p>Throughput & Speedup Comparison of 9 AI-Generated Maps vs java.util.HashMap</p>");
            out.println("    </div>");

            for (Map.Entry<String, List<BenchmarkEntry>> group : byOp.entrySet()) {
                String op = group.getKey();
                List<BenchmarkEntry> list = new ArrayList<>(group.getValue());
                list.sort((a, b) -> Double.compare(b.score(), a.score()));

                double maxScore = list.stream().mapToDouble(BenchmarkEntry::score).max().orElse(1.0);
                double baselineScore = list.stream().filter(e -> "javaMap".equals(e.targetId())).mapToDouble(BenchmarkEntry::score).findFirst().orElse(1.0);

                out.println("    <div class=\"card\">");
                out.printf("        <h2>Operation: <code>%s</code></h2>%n", op);
                out.println("        <table>");
                out.println("            <thead>");
                out.println("                <tr>");
                out.println("                    <th>Rank</th>");
                out.println("                    <th>Implementation</th>");
                out.println("                    <th>Model</th>");
                out.println("                    <th class=\"numeric\">Throughput (ops/µs)</th>");
                out.println("                    <th class=\"numeric\">Margin (±)</th>");
                out.println("                    <th class=\"numeric\">Speedup vs HashMap</th>");
                out.println("                </tr>");
                out.println("            </thead>");
                out.println("            <tbody>");

                int rank = 1;
                for (BenchmarkEntry e : list) {
                    double pct = (e.score() / maxScore) * 100.0;
                    double speedup = e.score() / baselineScore;
                    String speedupClass = speedup >= 1.05 ? "speedup-fast" : (speedup <= 0.95 ? "speedup-slow" : "");
                    String rankBadge = (rank == 1) ? "<span class=\"badge badge-winner\">🥇 #1</span>" : ("#" + rank);

                    out.println("                <tr>");
                    out.printf("                    <td>%s</td>%n", rankBadge);
                    out.printf("                    <td><strong>%s</strong></td>%n", e.targetId());
                    out.printf("                    <td><span class=\"badge badge-primary\">%s</span></td>%n", e.modelName());
                    out.printf("                    <td class=\"numeric\"><div class=\"bar-container\"><div class=\"bar-fill\" style=\"width: %.1f%%;\"></div></div> <strong>%,.2f</strong></td>%n", pct, e.score());
                    out.printf("                    <td class=\"numeric\">± %,.2f</td>%n", e.scoreError());
                    out.printf("                    <td class=\"numeric\"><span class=\"%s\">%.2fx</span></td>%n", speedupClass, speedup);
                    out.println("                </tr>");
                    rank++;
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

    public static void main(String[] args) throws Exception {
        File rootDir = GlobalDashboardGenerator.findRootDir();
        File jsonFile = new File(rootDir, "coverage-report/target/reports/jmh-results.json");
        if (!jsonFile.exists()) {
            jsonFile = new File(rootDir, "target/reports/jmh-results.json");
        }

        File reportsDir = new File(rootDir, "target/reports");
        generateReports(jsonFile, reportsDir);

        File covReports = new File(rootDir, "coverage-report/target/reports");
        if (covReports.exists()) {
            generateReports(jsonFile, covReports);
        }

        System.out.println("Reports generated successfully in " + reportsDir.getAbsolutePath());
    }
}
