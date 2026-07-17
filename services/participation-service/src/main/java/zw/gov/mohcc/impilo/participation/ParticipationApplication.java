package zw.gov.mohcc.impilo.participation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Participation — Citizen Get-Involved &amp; Co-Design service.
 *
 * <p>Owns the citizen contribution aggregate (ideas, experience feedback, testing sign-ups,
 * community-of-practice membership), a moderated public idea board, and testing-cohort
 * enrolment. This is the "something could be better" surface, distinct from Rito's
 * "something went wrong". Operating cycle: Receive → Community review → Explore → Research
 * → Select for co-design → Plan → Build → Ready for testing → Release (with Not-feasible
 * and Merge off-ramps).</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
public class ParticipationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParticipationApplication.class, args);
    }
}
