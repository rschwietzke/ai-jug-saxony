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

        File dashboardMd = new File(outputDir, "global-dashboard.md");
        File dashboardHtml = new File(outputDir, "global-dashboard.html");
        File indexHtml = new File(outputDir, "index.html");
        File fastHtml = new File(outputDir, "fasthashmap.html");
        File lruHtml = new File(outputDir, "lruclockmap.html");
        File xltHtml = new File(outputDir, "xlt-util.html");

        assertThat(dashboardMd).exists().isNotEmpty();
        assertThat(dashboardHtml).exists().isNotEmpty();
        assertThat(indexHtml).exists().isNotEmpty();
        assertThat(fastHtml).exists().isNotEmpty();
        assertThat(lruHtml).exists().isNotEmpty();
        assertThat(xltHtml).exists().isNotEmpty();
    }
}
