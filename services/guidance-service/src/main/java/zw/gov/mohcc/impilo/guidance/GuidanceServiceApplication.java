package zw.gov.mohcc.impilo.guidance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Guidance Service — Health OS §13: Conversational and Guidance Services.
 *
 * <p>Provides the intelligent, knowledge-enabled, conversational, and
 * context-aware guidance layer of the Health Operating System. The service
 * manages knowledge articles, health education content, reminders/prompts,
 * and conversational guidance sessions.</p>
 *
 * <p>Port: 8260</p>
 */
@SpringBootApplication
public class GuidanceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GuidanceServiceApplication.class, args);
    }
}
