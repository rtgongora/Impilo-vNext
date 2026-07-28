package zw.gov.mohcc.impilo.surgery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Surgery — the Surgery and Surgical Specialties domain pack (clinical plane, port 8396).
 *
 * <p>System of record for surgical DISEASE — a management-course record that attaches to
 * PCT journeys and never contains them. Nothing owns this today: no service has a surgical
 * episode, condition, staging, indication, outcome or surveillance model.</p>
 *
 * <p>Doctrine invariants, from
 * {@code docs/architecture/adr/ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES.md} §5/§5a
 * (the 2026-07-26 CC-2 amendment, which corrected the original decision 5 after review found
 * it would have violated CC-2's prohibition on person-level longitudinal registries):</p>
 *
 * <p><b>MAY own</b> — operational and phase truth of surgical care: the surgical episode as a
 * management-course record; structured surgical assessment content; operative indication and
 * the non-operative options weighed; prehabilitation and optimisation execution; planned-
 * versus-performed reconciliation; complication pathway INSTANCES as workflow (the resulting
 * problem lands in {@code pct_problems}); waiting-list clinical revalidation state; the
 * fifteen specialty extensions, their indications, operative content, templates and maps.</p>
 *
 * <p><b>MUST NOT own</b> — these are PCT's, and surgery attaches to them:</p>
 * <ul>
 *   <li>Surgical condition, diagnosis, certainty, severity, staging, recurrence —
 *       {@code pct_problems}; surgery writes with {@code responsible_service=surgery}.</li>
 *   <li>The surveillance plan — {@code pct_care_plans} + {@code pct_care_plan_goals}; surgery
 *       proposes and executes, does not own.</li>
 *   <li>The decision that opens or closes a phase of care — PCT, per the admission-handshake
 *       model; surgery owns the surgical reasoning and the options considered, PCT records
 *       the decision.</li>
 *   <li>Functional outcome, patient-reported outcome — PCT person-level; surgery contributes
 *       measurements.</li>
 *   <li>Serial burn assessment — {@code pct.burn_assessment} (emergency lane delegation);
 *       surgery writes through it, no parallel acute copy.</li>
 * </ul>
 *
 * <p>The four-question component test this service must keep answering
 * <b>reference · contribute · execute · stamp</b> on every future migration: does it
 * reference care journeys or contain them; does it contribute to PCT's longitudinal facts or
 * hold its own copy; does it execute a decision PCT owns or make the decision itself; does it
 * stamp a correlation id or claim to be a spine others stamp. CC-5 requires every clinical
 * episode to carry a resolvable PCT anchor — {@code inpatient-service} owns
 * {@code procedure_episode} for the operation itself; this service references it, never
 * duplicates it, and is NOT a second theatre workflow.</p>
 *
 * <p>S0 is the scaffold only: schema, outbox, health. The surgical episode itself lands in
 * S1, extending {@code pct_problems}/{@code pct_care_plans} per the CC-2 amendment rather than
 * duplicating them — the same reason P1 gave the procedure catalogue its own wave in the
 * sibling {@code procedures-service} programme.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
public class SurgeryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SurgeryApplication.class, args);
    }
}
