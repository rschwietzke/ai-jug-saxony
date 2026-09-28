package org.jugsaxony.report;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalJolReportTest {

    @Test
    public void testGenerateJolReport() throws Exception {
        File rootDir = GlobalDashboardGenerator.findRootDir();

        File outputDir = new File(rootDir, "reports");
        outputDir.mkdirs();

        GlobalJolReport.generateReports(outputDir);

        File mdReport = new File(outputDir, "jol-report.md");
        File htmlReport = new File(outputDir, "jol-report.html");

        assertThat(mdReport).exists().isNotEmpty();
        assertThat(htmlReport).exists().isNotEmpty();

        // Generate master dashboard and sub-section pages
        GlobalDashboardGenerator.generateDashboard(outputDir, rootDir);

        File jmhJson = new File(outputDir, "jmh-results.json");
        if (jmhJson.exists()) {
            GlobalJmhReportGenerator.generateReports(jmhJson, outputDir);
        }

        File dashboardMd = new File(outputDir, "global-dashboard.md");
        File dashboardHtml = new File(outputDir, "global-dashboard.html");
        File indexHtml = new File(outputDir, "index.html");
        File fastHtml = new File(outputDir, "fasthashmap.html");
        File lruHtml = new File(outputDir, "lruclockmap.html");
        File xltHtml = new File(outputDir, "xlt-util.html");
        File simpleMathHtml = new File(outputDir, "simplemath.html");
        File surefireHtml = new File(outputDir, "surefire.html");

        assertThat(dashboardMd).exists().isNotEmpty();
        assertThat(dashboardHtml).exists().isNotEmpty();
        assertThat(indexHtml).exists().isNotEmpty();
        assertThat(fastHtml).exists().isNotEmpty();
        assertThat(lruHtml).exists().isNotEmpty();
        assertThat(xltHtml).exists().isNotEmpty();
        assertThat(simpleMathHtml).exists().isNotEmpty();
        assertThat(surefireHtml).exists().isNotEmpty();

        File demo0SimpleMathHtml = new File(outputDir, "sources/demo0/SimpleMath.html");
        File demo0SimpleMathJava = new File(outputDir, "sources/demo0/SimpleMath.java");
        File demo0SimpleMathTestHtml = new File(outputDir, "sources/demo0/SimpleMathTest.html");
        File demo0SimpleMathTestJava = new File(outputDir, "sources/demo0/SimpleMathTest.java");
        File demo0FastHashMapHtml = new File(outputDir, "sources/demo0/FastHashMap.html");
        File demo0FastHashMapTestHtml = new File(outputDir, "sources/demo0/FastHashMapTest.html");
        File demo0LruClockMapHtml = new File(outputDir, "sources/demo0/LRUClockMap.html");
        File demo0LruClockMapTestHtml = new File(outputDir, "sources/demo0/LRUClockMapTest.html");

        assertThat(demo0SimpleMathHtml).exists().isNotEmpty();
        assertThat(demo0SimpleMathJava).exists().isNotEmpty();
        assertThat(demo0SimpleMathTestHtml).exists().isNotEmpty();
        assertThat(demo0SimpleMathTestJava).exists().isNotEmpty();
        assertThat(demo0FastHashMapHtml).exists().isNotEmpty();
        assertThat(demo0FastHashMapTestHtml).exists().isNotEmpty();
        assertThat(demo0LruClockMapHtml).exists().isNotEmpty();
        assertThat(demo0LruClockMapTestHtml).exists().isNotEmpty();
    }
}
