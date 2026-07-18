package zw.gov.mohcc.impilo.tshepo.identity.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.EventEnvelope;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes clinical-plane subject events on the {@code impilo.identity.subject}
 * topic (Identity Contract §7.3).
 *
 * <p>These are the ONLY identity-lifecycle events clinical services may consume.
 * Payloads carry CPIDs exclusively — never a Health ID, CRID, or any PII. The
 * payload keys are a strict allowlist enforced by the relay's translation step
 * and by {@code SubjectTranslationRelayTest}.</p>
 *
 * <p>Events are partitioned by CPID (message key) so per-subject ordering is
 * preserved. When called inside a transaction, the send is deferred to
 * after-commit so a rolled-back mapping never leaks an event.</p>
 */
@Component
public class SubjectEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SubjectEventPublisher.class);

    /** Producer id for the clinical-plane subject stream. */
    private static final EventTopicRegistry REGISTRY = new EventTopicRegistry("identity");

    /** Single topic for all subject lifecycle events — per-subject ordering by CPID key. */
    public static final String SUBJECT_TOPIC = REGISTRY.v11Topic("subject");

    private final KafkaTemplate<String, String> kafkaTemplate;

    public SubjectEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void subjectCreated(UUID tenantId, UUID cpid, String status, String correlationId) {
        publish("created", tenantId, cpid,
                Map.of("tenant_id", tenantId.toString(),
                       "cpid", cpid.toString(),
                       "status", status != null ? status : "PROVISIONAL"),
                correlationId);
    }

    public void subjectVerified(UUID tenantId, UUID cpid, String correlationId) {
        publish("verified", tenantId, cpid,
                Map.of("tenant_id", tenantId.toString(),
                       "cpid", cpid.toString(),
                       "verified", Boolean.TRUE),
                correlationId);
    }

    public void subjectDeceased(UUID tenantId, UUID cpid, String correlationId) {
        publish("deceased", tenantId, cpid,
                Map.of("tenant_id", tenantId.toString(),
                       "cpid", cpid.toString()),
                correlationId);
    }

    public void subjectMerged(UUID tenantId, UUID survivorCpid, UUID mergedCpid, String correlationId) {
        publish("merged", tenantId, survivorCpid,
                Map.of("tenant_id", tenantId.toString(),
                       "survivor_cpid", survivorCpid.toString(),
                       "merged_cpid", mergedCpid.toString()),
                correlationId);
    }

    public void subjectMergeReversed(UUID tenantId, UUID survivorCpid, UUID mergedCpid, String correlationId) {
        publish("merge_reversed", tenantId, survivorCpid,
                Map.of("tenant_id", tenantId.toString(),
                       "survivor_cpid", survivorCpid.toString(),
                       "merged_cpid", mergedCpid.toString()),
                correlationId);
    }

    public void subjectReconciled(UUID tenantId, UUID provisionalCpid, UUID canonicalCpid, String correlationId) {
        publish("reconciled", tenantId, canonicalCpid,
                Map.of("tenant_id", tenantId.toString(),
                       "provisional_cpid", provisionalCpid.toString(),
                       "canonical_cpid", canonicalCpid.toString()),
                correlationId);
    }

    private void publish(String action, UUID tenantId, UUID partitionCpid,
                         Map<String, Object> payload, String correlationId) {
        String eventType = REGISTRY.eventType("subject", action);
        try {
            String corr = correlationId != null ? correlationId : UUID.randomUUID().toString();
            String eventId = UUID.randomUUID().toString();
            EventEnvelope envelope = EventEnvelope.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .schemaVersion(1)
                    .correlationId(corr)
                    .causationId(corr)
                    .idempotencyKey(eventId)
                    .producer("tshepo-identity")
                    .tenantId(tenantId.toString())
                    .podId("national")
                    .occurredAt(OffsetDateTime.now())
                    .emittedAt(OffsetDateTime.now())
                    .subjectType("Patient")
                    .subjectId(partitionCpid.toString())
                    .payload(payload)
                    .build();
            String json = CompanionOutboxPublisher.serializeEnvelope(envelope);
            sendAfterCommitIfTransactional(eventType, partitionCpid.toString(), json);
        } catch (Exception e) {
            // A failed subject emission must fail the relay's listener so Kafka
            // redelivers — swallowing it would silently drop identity propagation.
            throw new IllegalStateException("Failed to publish " + eventType, e);
        }
    }

    private void sendAfterCommitIfTransactional(String eventType, String key, String json) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(eventType, key, json);
                }
            });
        } else {
            doSend(eventType, key, json);
        }
    }

    private void doSend(String eventType, String key, String json) {
        kafkaTemplate.send(SUBJECT_TOPIC, key, json);
        log.info("Published {} key={} to {}", eventType, key, SUBJECT_TOPIC);
    }
}
