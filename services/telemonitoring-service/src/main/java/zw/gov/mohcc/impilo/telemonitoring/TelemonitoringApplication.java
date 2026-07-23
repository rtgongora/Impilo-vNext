package zw.gov.mohcc.impilo.telemonitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Telemonitoring — Community Remote Monitoring service (clinical plane, Vol II §14, OF-B22).
 *
 * <p>Owns the {@code MonitoringPlan} / {@code ThresholdProfile} lifecycle (this epic) and,
 * in later OF-C epics, {@code AlertRule}/{@code AlertEpisode} (OF-B26), device clinical
 * assignment (OF-B24) and the single-writer monitoring-band Observation path to BUTANO
 * (OF-B25). Doctrine invariants:</p>
 * <ul>
 *   <li>Plans are PRESCRIBED — enrolment rides the OROS order spine (OrderType OTHER with a
 *       MONITORING order-code discriminator pending OD-16).</li>
 *   <li>Automated suggestions MUST NOT self-activate — a plan becomes ACTIVE only on
 *       explicit clinician approval, and no patient notification happens pre-approval.</li>
 *   <li>This service never talks to devices (iot-ingestion = connectivity truth,
 *       asset-registry = physical/calibration truth) and never duplicates the
 *       surveillance (population rules) or wellness (simba) domains.</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
public class TelemonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemonitoringApplication.class, args);
    }
}
