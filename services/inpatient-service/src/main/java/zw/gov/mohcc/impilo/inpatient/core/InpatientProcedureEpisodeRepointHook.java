package zw.gov.mohcc.impilo.inpatient.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureEpisodeRepository;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointCommand;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointHook;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Wave 5b §15 — PROVISIONAL-patient identity repoint for the THEATRE record.
 *
 * <p>An unidentified trauma patient carries a PROVISIONAL CPID on {@code procedure_episode.subject_cpid}.
 * When the patient is later reconciled ({@code POST /internal/v1/patients/merge} → VITO
 * {@code vito.merge.executed}), the merged (tombstoned) Health ID must be repointed to the surviving
 * Health ID so the surgery record is not orphaned.</p>
 *
 * <p>This is a self-contained {@link IdentityRepointHook} participant scoped to the inpatient
 * {@code procedure_episode} table only. It is deliberately LEFT UNWIRED here: the single inpatient
 * consumer of {@code vito.merge.executed} (a Spring {@code List<IdentityRepointHook>} fan-out, owned by
 * the trauma lane's W6) collects this {@code @Component} automatically. It does NOT add a second Kafka
 * listener, and it does NOT touch the trauma lane's {@code resuscitation_record} hook.</p>
 */
@Component
public class InpatientProcedureEpisodeRepointHook implements IdentityRepointHook {

    private static final Logger log = LoggerFactory.getLogger(InpatientProcedureEpisodeRepointHook.class);

    private final ProcedureEpisodeRepository episodeRepository;
    private final EventOutboxRepository outboxRepository;

    public InpatientProcedureEpisodeRepointHook(ProcedureEpisodeRepository episodeRepository,
                                                EventOutboxRepository outboxRepository) {
        this.episodeRepository = episodeRepository;
        this.outboxRepository = outboxRepository;
    }

    @Override
    public String participant() {
        // Table-specific so the W6 fan-out dispatch log distinguishes this from resuscitation_record.
        return "inpatient.procedure_episode";
    }

    /**
     * Repoint every theatre case still on the merged Health ID onto the survivor, in this hook's own
     * transaction. Idempotent by construction: after the first apply no {@code subject_cpid} still equals
     * {@code mergedHealthId}, so a redelivered command repoints 0 rows. Each invocation writes an audit
     * outbox row keyed by {@code command.idempotencyKey()}.
     */
    @Override
    @Transactional
    public int repoint(IdentityRepointCommand command) {
        UUID tenantId;
        try {
            tenantId = UUID.fromString(command.tenantId());
        } catch (IllegalArgumentException e) {
            log.warn("INPATIENT-THEATRE: identity repoint has a malformed tenant id {} — skipping: {}",
                    command.tenantId(), e.getMessage());
            return 0;
        }
        int repointed = episodeRepository.repointSubjectCpid(
                tenantId, command.mergedHealthId(), command.survivorHealthId());
        writeAudit(command, tenantId, repointed);
        if (repointed > 0) {
            log.info("INPATIENT-THEATRE: repointed {} theatre case(s) {} → {} (merge {})",
                    repointed, command.mergedHealthId(), command.survivorHealthId(), command.mergeId());
        }
        return repointed;
    }

    private void writeAudit(IdentityRepointCommand command, UUID tenantId, int repointed) {
        EventOutboxEntity audit = new EventOutboxEntity();
        audit.setAggregateType("PROCEDURE");
        audit.setAggregateId(command.mergeId() != null ? command.mergeId() : command.idempotencyKey());
        audit.setTenantId(tenantId.toString());
        audit.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
        audit.setCorrelationId(command.correlationId() != null ? command.correlationId() : UUID.randomUUID().toString());
        audit.setIdempotencyKey(command.idempotencyKey());
        audit.setEventType("inpatient.procedure_episode.identity-repointed");
        audit.setSchemaVersion(1);
        audit.setOccurredAt(OffsetDateTime.now());
        audit.setPayloadJson("{"
                + "\"event_type\":\"inpatient.procedure_episode.identity-repointed\","
                + "\"tenant_id\":\"" + tenantId + "\","
                + "\"merged_health_id\":\"" + command.mergedHealthId() + "\","
                + "\"survivor_health_id\":\"" + command.survivorHealthId() + "\","
                + "\"merge_id\":\"" + (command.mergeId() != null ? command.mergeId() : "") + "\","
                + "\"rows_repointed\":" + repointed + ","
                + "\"idempotency_key\":\"" + command.idempotencyKey() + "\""
                + "}");
        outboxRepository.save(audit);
    }
}
