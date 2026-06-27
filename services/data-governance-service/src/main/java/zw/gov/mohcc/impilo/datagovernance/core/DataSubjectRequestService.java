package zw.gov.mohcc.impilo.datagovernance.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.datagovernance.domain.DataSubjectRequestEntity;
import zw.gov.mohcc.impilo.datagovernance.repository.DataSubjectRequestRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the lifecycle of data-subject rights requests (account deletion / erasure)
 * for the Privacy-by-Architecture plane (§6). Persists the authoritative request
 * ledger and emits domain events; downstream consumers perform the actual erasure
 * (Keycloak deactivation, VITO PII anonymisation, consent revocation).
 */
@Service
public class DataSubjectRequestService {

    private static final Logger log = LoggerFactory.getLogger(DataSubjectRequestService.class);

    private static final String DEFAULT_TYPE = "ACCOUNT_DELETION";
    private static final Set<String> VALID_TYPES = Set.of("ACCOUNT_DELETION");
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "PROCESSING");
    private static final String AGGREGATE = "DataSubjectRequest";

    private final DataSubjectRequestRepository repository;
    private final PrivacyOutboxAppender outbox;

    public DataSubjectRequestService(DataSubjectRequestRepository repository,
                                     PrivacyOutboxAppender outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    static String normaliseType(String requestType) {
        if (requestType == null || requestType.isBlank()) {
            return DEFAULT_TYPE;
        }
        String upper = requestType.trim().toUpperCase();
        if (!VALID_TYPES.contains(upper)) {
            throw new IllegalArgumentException("Unsupported requestType: " + requestType);
        }
        return upper;
    }

    /**
     * Submit a deletion request. Idempotent: if an in-flight request already exists
     * for this subject and type, the existing one is returned unchanged (no duplicate
     * event). Otherwise a PENDING request is persisted and a submitted event emitted.
     */
    @Transactional
    public DataSubjectRequestEntity submit(String subjectId, String requestType, String reason,
                                           UUID tenantId, String podId, String correlationId,
                                           String idempotencyKey) {
        String type = normaliseType(requestType);

        Optional<DataSubjectRequestEntity> active = repository
                .findFirstByTenantIdAndSubjectIdAndRequestTypeAndStatusInOrderByRequestedAtDesc(
                        tenantId, subjectId, type, ACTIVE_STATUSES);
        if (active.isPresent()) {
            log.info("Deletion request already in-flight [subject={}, status={}] — idempotent return",
                    subjectId, active.get().getStatus());
            return active.get();
        }

        DataSubjectRequestEntity req = new DataSubjectRequestEntity(
                tenantId, subjectId, type, reason, correlationId);
        repository.save(req);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", req.getRequestId().toString());
        payload.put("subject_id", subjectId);
        payload.put("request_type", type);
        payload.put("status", req.getStatus());
        payload.put("reason", reason);

        outbox.append("impilo.data.governance.data-subject-request.submitted.v1",
                AGGREGATE, req.getRequestId().toString(),
                tenantId, podId, correlationId,
                idempotencyKey != null ? idempotencyKey : "dsr-submit-" + req.getRequestId(),
                payload, subjectId, subjectId, "DataSubject");

        log.info("Submitted data-subject request [requestId={}, subject={}, type={}]",
                req.getRequestId(), subjectId, type);
        return req;
    }

    /** Latest request for the subject (any status), or empty if none ever submitted. */
    @Transactional(readOnly = true)
    public Optional<DataSubjectRequestEntity> latest(String subjectId, String requestType, UUID tenantId) {
        return repository.findFirstByTenantIdAndSubjectIdAndRequestTypeOrderByRequestedAtDesc(
                tenantId, subjectId, normaliseType(requestType));
    }

    /**
     * Cancel a pending request. Returns the cancelled request, or empty if there is
     * no PENDING request to cancel (PROCESSING/COMPLETED cannot be cancelled).
     */
    @Transactional
    public Optional<DataSubjectRequestEntity> cancel(String subjectId, String requestType,
                                                     UUID tenantId, String podId,
                                                     String correlationId, String idempotencyKey) {
        String type = normaliseType(requestType);

        Optional<DataSubjectRequestEntity> activeOpt = repository
                .findFirstByTenantIdAndSubjectIdAndRequestTypeAndStatusInOrderByRequestedAtDesc(
                        tenantId, subjectId, type, ACTIVE_STATUSES);
        if (activeOpt.isEmpty() || !"PENDING".equals(activeOpt.get().getStatus())) {
            return Optional.empty();
        }

        DataSubjectRequestEntity req = activeOpt.get();
        OffsetDateTime now = OffsetDateTime.now();
        req.setStatus("CANCELLED");
        req.setCancelledAt(now);
        req.setUpdatedAt(now);
        repository.save(req);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", req.getRequestId().toString());
        payload.put("subject_id", subjectId);
        payload.put("request_type", type);
        payload.put("status", req.getStatus());
        payload.put("cancelled_at", now.toString());

        outbox.append("impilo.data.governance.data-subject-request.cancelled.v1",
                AGGREGATE, req.getRequestId().toString(),
                tenantId, podId, correlationId,
                idempotencyKey != null ? idempotencyKey : "dsr-cancel-" + req.getRequestId(),
                payload, subjectId, subjectId, "DataSubject");

        log.info("Cancelled data-subject request [requestId={}, subject={}]",
                req.getRequestId(), subjectId);
        return Optional.of(req);
    }
}
