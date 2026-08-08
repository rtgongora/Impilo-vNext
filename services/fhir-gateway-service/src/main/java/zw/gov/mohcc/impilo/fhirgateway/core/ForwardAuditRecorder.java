package zw.gov.mohcc.impilo.fhirgateway.core;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirAuditLogEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirAuditLogRepository;

/**
 * Writes the audit row and its outbox event together, in a transaction that holds no network call.
 *
 * <p>{@code GatewayForwardService.forward} was annotated {@code @Transactional} around its whole
 * body — which includes a call to the consent service and a call to the destination FHIR server. A
 * Postgres connection was therefore held for the duration of two HTTP round-trips, on every
 * forward. With one caller that is waste; with the four services P5 is about to route through here,
 * it is a connection-pool exhaustion waiting for its first slow downstream.</p>
 *
 * <p>Extracting the persistence into its own bean is what makes that fixable: Spring proxies do not
 * intercept self-invocation, so a private method on the same class annotated {@code @Transactional}
 * would have silently done nothing — the annotation would be there and the transaction would not.</p>
 *
 * <p>The two saves stay atomic. An audit row without its outbox event is a delivery nothing
 * downstream learns about; an outbox event without its audit row points at a decision with no
 * record. {@code REQUIRES_NEW} so the record survives even when an outer transaction rolls back:
 * the point of an audit row is that it outlives the thing it describes.</p>
 */
@Component
public class ForwardAuditRecorder {

    private final FhirAuditLogRepository auditLogRepository;
    private final EventOutboxRepository outboxRepository;

    public ForwardAuditRecorder(FhirAuditLogRepository auditLogRepository,
                                EventOutboxRepository outboxRepository) {
        this.auditLogRepository = auditLogRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Persists the audit row, then the outbox event that references it.
     *
     * @param eventBuilder builds the outbox event from the saved audit row — it needs the generated
     *                     id, so it cannot be constructed before the save
     * @return the saved audit row
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FhirAuditLogEntity record(FhirAuditLogEntity auditLog,
                                     java.util.function.Function<FhirAuditLogEntity, EventOutboxEntity> eventBuilder) {
        FhirAuditLogEntity saved = auditLogRepository.save(auditLog);
        outboxRepository.save(eventBuilder.apply(saved));
        return saved;
    }
}
