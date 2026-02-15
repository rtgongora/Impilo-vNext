package zw.gov.mohcc.impilo.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.pipeline.config.PipelineProperties;

/**
 * Entry point for the Data Pipeline service.
 *
 * <p>v1.1-native service that ingests EventEnvelope events via internal API,
 * writes curated pipeline records, and maintains per-source watermarks
 * for exactly-once processing guarantees.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PipelineProperties.class)
public class DataPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataPipelineApplication.class, args);
    }
}
