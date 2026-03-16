package zw.gov.mohcc.impilo.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.reporting.config.ReportingProperties;

/**
 * Entry point for the Reporting service.
 *
 * <p>Report definition registry and run engine providing on-demand and
 * scheduled report generation with JSON/CSV export formats. Executes
 * parameterized SQL queries against the reporting database with
 * tenant isolation and DML/DDL safety guards.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ReportingProperties.class)
public class ReportingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingApplication.class, args);
    }
}
