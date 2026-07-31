package zw.gov.mohcc.impilo.khuluma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.realtime.RealtimeCoreConfiguration;

/**
 * Khuluma — Impilo Comms Hub. The native coordination & communication layer of the Health OS:
 * unified conversations, presence, calls/meetings, escalation and notification orchestration,
 * reusing channels/notification/live/rtc-gateway/pct as systems-of-record.
 */
@SpringBootApplication
@EnableScheduling
@Import(RealtimeCoreConfiguration.class)
public class KhulumaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KhulumaServiceApplication.class, args);
    }
}
