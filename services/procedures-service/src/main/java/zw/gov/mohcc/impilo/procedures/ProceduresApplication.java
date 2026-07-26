package zw.gov.mohcc.impilo.procedures;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Procedures — the Clinical Procedures Pipeline platform layer (clinical plane, port 8395).
 *
 * <p>A cross-cutting capability reusable by Emergency Care, Medicine, Surgery, Theatre,
 * Anaesthesia, Paediatrics, Reproductive and Maternity care, Critical Care, Mental Health,
 * Dentistry, Rehabilitation, Nursing, allied health, Laboratory, and imaging and
 * interventional services. It answers whether a procedure is <em>safely ready</em>, who may
 * perform it, and what it requires. The owning specialty answers whether it is clinically
 * right and what the result means.</p>
 *
 * <p>Doctrine invariants, from
 * {@code docs/architecture/adr/ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES.md}:</p>
 * <ul>
 *   <li><b>Engine-not-store.</b> This service evaluates; the executing service persists.
 *       Readiness verdicts and safety-pause completions are computed here and recorded by
 *       whoever runs the procedure. {@code inpatient.procedure_readiness_check} and
 *       {@code procedure_checklist_item} stay the per-episode record of truth. Two records
 *       of one fact is the duplication the guardrails forbid, and not making that mistake
 *       is what makes this service safe to add rather than a second system of record.</li>
 *   <li><b>Fail safe.</b> This service sits on the readiness and competence path, so an
 *       outage must block a procedure it cannot clear rather than allow it. Emergency
 *       override is audited, never implicit — and never a silent bypass.</li>
 *   <li><b>Fail safe extends to the client.</b> An unreadable catalogue is not an empty
 *       catalogue; an unreadable readiness verdict is not a clear readiness; an unreadable
 *       privilege is not permission. Every consumer distinguishes empty, unknown and
 *       unavailable, and renders the third as failure.</li>
 *   <li><b>Never delays emergency care.</b> Not for financial, administrative or
 *       connectivity reasons.</li>
 * </ul>
 *
 * <p>It must not own: the procedure request record or its fulfilment lifecycle (oros-service,
 * {@code order_type=PROCEDURE} behind the {@code ProcedureWorkflow} guard); the execution
 * record, specimens, devices, counts or recovery (inpatient-service); consent (mvumo);
 * terminology (zibo); decision logic (clinical-knowledge-platform); nor — per
 * care-continuum-doctrine CC-2 — person-level longitudinal clinical registries or the
 * clinical decision that opens or closes a phase of care, which are PCT's.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
public class ProceduresApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProceduresApplication.class, args);
    }
}
