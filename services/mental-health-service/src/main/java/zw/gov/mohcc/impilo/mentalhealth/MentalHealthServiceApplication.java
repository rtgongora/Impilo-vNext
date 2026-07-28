package zw.gov.mohcc.impilo.mentalhealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mental Health service (W13, port 8397).
 *
 * <p>The accepting counterparty for a psychiatric emergency handover (pct's
 * {@code emergency_handover(target_type=MENTAL_HEALTH)}). A Care Continuum <b>component</b> —
 * {@code continuum_parent: pct-service} — that owns psychiatric operational and phase truth:
 * referral, assessment, risk formulation, safety planning, involuntary-episode legal basis, a
 * restraint-reduction register, admission requests that raise to PCT without deciding, and
 * follow-up. Per CC-2 it must NOT own the admission decision, person-level longitudinal
 * registries, or a second care continuum.
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
public class MentalHealthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MentalHealthServiceApplication.class, args);
    }
}
