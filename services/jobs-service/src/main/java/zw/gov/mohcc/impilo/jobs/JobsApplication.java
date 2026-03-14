package zw.gov.mohcc.impilo.jobs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Jobs Service.
 *
 * <p>Manages job definitions, triggers, and execution records.
 * Publishes domain events via the outbox pattern to Kafka.</p>
 */
@SpringBootApplication
@EnableScheduling
public class JobsApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobsApplication.class, args);
    }
}
