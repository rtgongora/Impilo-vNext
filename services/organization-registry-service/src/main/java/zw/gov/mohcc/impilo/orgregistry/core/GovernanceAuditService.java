package zw.gov.mohcc.impilo.orgregistry.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ResponsibilityAuditEventEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.ResponsibilityAuditEventRepository;

import java.util.UUID;

/**
 * Writes governance audit evidence (v1.3.8 §Scope G).
 *
 * <p>Two properties matter more than convenience. First, refusals are recorded as carefully
 * as permissions — an unaudited refusal is indistinguishable from an operation that quietly
 * did nothing. Second, {@link #recordOrFail} propagates a write failure to the caller: a
 * consequential governance transition must not proceed when its evidence cannot be produced.
 */
@Service
public class GovernanceAuditService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceAuditService.class);

    private final ResponsibilityAuditEventRepository repository;

    public GovernanceAuditService(ResponsibilityAuditEventRepository repository) {
        this.repository = repository;
    }

    /** Mutable builder for the audit record; keeps call sites readable at ~14 fields. */
    public static final class Entry {
        private final ResponsibilityAuditEventEntity e = new ResponsibilityAuditEventEntity();

        public Entry(String operation, String targetType, String decision) {
            e.setAuditEventId(UUID.randomUUID());
            e.setOperation(operation);
            e.setTargetType(targetType);
            e.setDecision(decision);
        }

        public Entry actor(String id, String authority, String scopeType, UUID scopeId) {
            e.setActorId(id);
            e.setActorAuthority(authority);
            e.setActorScopeType(scopeType);
            e.setActorScopeId(scopeId);
            return this;
        }

        public Entry target(UUID id) { e.setTargetId(id); return this; }
        public Entry trustDomain(UUID id) { e.setTrustDomainId(id); return this; }
        public Entry organisation(UUID id) { e.setOrganisationId(id); return this; }
        public Entry versions(Integer previous, Integer next) {
            e.setPreviousVersion(previous);
            e.setNewVersion(next);
            return this;
        }
        public Entry reason(String code, String detail) {
            e.setReasonCode(code);
            e.setReasonDetail(detail);
            return this;
        }
        public Entry correlation(String correlationId, String requestId) {
            e.setCorrelationId(correlationId);
            e.setRequestId(requestId);
            return this;
        }

        ResponsibilityAuditEventEntity entity() { return e; }
    }

    /**
     * Record audit evidence for a consequential transition. A failure here fails the caller:
     * activation and controller-dependent resolution must not silently proceed unaudited.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordOrFail(Entry entry) {
        repository.saveAndFlush(entry.entity());
    }

    /**
     * Record audit evidence for a refusal, in its own transaction so it survives the caller's
     * rollback. A refusal that vanishes with the transaction that caused it is not evidence.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefusal(Entry entry) {
        try {
            repository.saveAndFlush(entry.entity());
        } catch (RuntimeException ex) {
            // Never let audit failure mask the original refusal, but never lose it silently.
            log.error("Failed to persist refusal audit for operation={} target={}",
                    entry.entity().getOperation(), entry.entity().getTargetId(), ex);
            throw ex;
        }
    }
}
