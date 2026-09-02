package org.jugsaxony.report;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalJolReportTest {

    @Test
    public void testGenerateJolReport() throws Exception {
        File rootDir = GlobalDashboardGenerator.findRootDir();

        File outputDir = new File(rootDir, "target/reports");
        outputDir.mkdirs();

        GlobalJolReport.generateReports(outputDir);

        File mdReport = new File(outputDir, "jol-report.md");
        File htmlReport = new File(outputDir, "jol-report.html");

        assertThat(mdReport).exists().isNotEmpty();
        assertThat(htmlReport).exists().isNotEmpty();

        // Also generate in coverage-report/target/reports if run locally
        File localReports = new File("target/reports");
        if (localReports.exists()) {
            GlobalJolReport.generateReports(localReports);
        }

        // Also generate the master dashboard
        GlobalDashboardGenerator.generateDashboard(outputDir, rootDir);
        if (localReports.exists()) {
            GlobalDashboardGenerator.generateDashboard(localReports, rootDir);
        }

        File dashboardMd = new File(outputDir, "global-dashboard.md");
        File dashboardHtml = new File(outputDir, "global-dashboard.html");

        assertThat(dashboardMd).exists().isNotEmpty();
        assertThat(dashboardHtml).exists().isNotEmpty();
    }
}
