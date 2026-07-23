package zw.gov.mohcc.impilo.msikaflow.core.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingType;
import zw.gov.mohcc.impilo.msikaflow.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.AnomalyFindingEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.AnomalyFindingRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OF-B29 — accountable finding persistence + lifecycle (Vol II §13.7/§21.5).
 *
 * <p>Recording is idempotent on {@code (tenantId, dedupeKey)}: sweeps re-run
 * freely without re-flagging the same fact. Every NEW finding writes one
 * outbox row ({@code ANOMALY_DETECTED} → topic
 * {@code msika.flow.anomaly.detected.v1}) whose payload is coded and PII-free:
 * ids, counts, thresholds — never names, addresses, clinical content or actor
 * identifiers.</p>
 *
 * <p>Lifecycle: OPEN → REVIEWED → DISMISSED (or OPEN → DISMISSED); both
 * transitions are reason-bound and stamp the accountable reviewer. Findings
 * never auto-close and are never deleted.</p>
 */
@Service
public class AnomalyFindingService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyFindingService.class);

    public static final String SEVERITY_INFO = "INFO";
    public static final String SEVERITY_WARN = "WARN";
    public static final String SEVERITY_HIGH = "HIGH";

    private final AnomalyFindingRepository findingRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AnomalyFindingService(AnomalyFindingRepository findingRepository,
                                 EventOutboxRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.findingRepository = findingRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /** Immutable description of a detected fact, produced by a monitor. */
    public record FindingCandidate(UUID tenantId,
                                   AnomalyFindingType type,
                                   String severity,
                                   String subjectType,
                                   String subjectRef,
                                   String dedupeKey,
                                   OffsetDateTime windowStart,
                                   OffsetDateTime windowEnd,
                                   Map<String, Object> details) {}

    /**
     * Record a finding if its dedupe key is unseen for the tenant.
     *
     * @return the persisted new finding, or empty when deduplicated.
     */
    @Transactional
    public Optional<AnomalyFindingEntity> record(FindingCandidate candidate) {
        if (findingRepository.findFirstByTenantIdAndDedupeKey(
                candidate.tenantId(), candidate.dedupeKey()).isPresent()) {
            return Optional.empty();
        }
        AnomalyFindingEntity finding = new AnomalyFindingEntity();
        finding.setFindingId(UlidGenerator.generate());
        finding.setTenantId(candidate.tenantId());
        finding.setFindingType(candidate.type());
        finding.setSeverity(candidate.severity());
        finding.setSubjectType(candidate.subjectType());
        finding.setSubjectRef(candidate.subjectRef());
        finding.setDedupeKey(candidate.dedupeKey());
        finding.setWindowStart(candidate.windowStart());
        finding.setWindowEnd(candidate.windowEnd());
        finding.setStatus(AnomalyFindingStatus.OPEN);
        if (candidate.details() != null && !candidate.details().isEmpty()) {
            try {
                finding.setDetailsJson(objectMapper.writeValueAsString(candidate.details()));
            } catch (Exception e) {
                log.warn("Unserialisable finding details for {}: {}", candidate.dedupeKey(), e.getMessage());
            }
        }
        finding = findingRepository.save(finding);
        emitDetectedEvent(finding);
        log.info("Anomaly finding recorded: type={} subject={}:{} severity={} id={}",
                finding.getFindingType(), finding.getSubjectType(), finding.getSubjectRef(),
                finding.getSeverity(), finding.getFindingId());
        return Optional.of(finding);
    }

    /**
     * The {@code msika.flow.anomaly.detected.v1} payload — coded and PII-free
     * by construction: the fixed key set below plus the finding's coded
     * details map (ids/counts/thresholds only; monitors never place actor
     * identifiers, names or free-text narratives in details).
     */
    private void emitDetectedEvent(AnomalyFindingEntity finding) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("findingId", finding.getFindingId());
            payload.put("findingType", finding.getFindingType().name());
            payload.put("severity", finding.getSeverity());
            payload.put("subjectType", finding.getSubjectType());
            payload.put("subjectRef", finding.getSubjectRef());
            payload.put("tenantId", finding.getTenantId().toString());
            payload.put("status", finding.getStatus().name());
            if (finding.getWindowStart() != null) {
                payload.put("windowStart", finding.getWindowStart().toString());
            }
            if (finding.getWindowEnd() != null) {
                payload.put("windowEnd", finding.getWindowEnd().toString());
            }
            if (finding.getDetailsJson() != null) {
                payload.put("details", objectMapper.readTree(finding.getDetailsJson()));
            }
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("AnomalyFinding");
            outbox.setAggregateId(finding.getFindingId());
            outbox.setEventType("ANOMALY_DETECTED");
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(finding.getTenantId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write anomaly outbox event for {}: {}",
                    finding.getFindingId(), e.getMessage());
        }
    }

    // ── accountable review lifecycle ─────────────────────────────────────

    /** OPEN → REVIEWED. Reason-bound; stamps the accountable reviewer. */
    @Transactional
    public AnomalyFindingEntity review(String findingId, UUID tenantId, String reviewerActorId, String reason) {
        AnomalyFindingEntity finding = requireFinding(findingId, tenantId);
        requireReason(reason);
        requireReviewer(reviewerActorId);
        if (finding.getStatus() != AnomalyFindingStatus.OPEN) {
            throw new IllegalStateException(
                    "FINDING_NOT_OPEN: cannot review a " + finding.getStatus() + " finding");
        }
        finding.setStatus(AnomalyFindingStatus.REVIEWED);
        stampReview(finding, reviewerActorId, reason);
        return findingRepository.save(finding);
    }

    /** OPEN|REVIEWED → DISMISSED (terminal). Reason-bound. */
    @Transactional
    public AnomalyFindingEntity dismiss(String findingId, UUID tenantId, String reviewerActorId, String reason) {
        AnomalyFindingEntity finding = requireFinding(findingId, tenantId);
        requireReason(reason);
        requireReviewer(reviewerActorId);
        if (finding.getStatus() == AnomalyFindingStatus.DISMISSED) {
            throw new IllegalStateException("FINDING_TERMINAL: finding already dismissed");
        }
        finding.setStatus(AnomalyFindingStatus.DISMISSED);
        stampReview(finding, reviewerActorId, reason);
        return findingRepository.save(finding);
    }

    @Transactional(readOnly = true)
    public AnomalyFindingEntity get(String findingId, UUID tenantId) {
        return requireFinding(findingId, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<AnomalyFindingEntity> list(UUID tenantId, AnomalyFindingStatus status,
                                           AnomalyFindingType type, Pageable pageable) {
        if (status != null && type != null) {
            return findingRepository.findByTenantIdAndStatusAndFindingTypeOrderByCreatedAtDesc(
                    tenantId, status, type, pageable);
        }
        if (status != null) {
            return findingRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, pageable);
        }
        if (type != null) {
            return findingRepository.findByTenantIdAndFindingTypeOrderByCreatedAtDesc(tenantId, type, pageable);
        }
        return findingRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    private AnomalyFindingEntity requireFinding(String findingId, UUID tenantId) {
        return findingRepository.findByFindingIdAndTenantId(findingId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("FINDING_NOT_FOUND: " + findingId));
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("REASON_REQUIRED: review/dismiss is reason-bound");
        }
    }

    private static void requireReviewer(String reviewerActorId) {
        if (reviewerActorId == null || reviewerActorId.isBlank()) {
            throw new IllegalArgumentException("REVIEWER_REQUIRED: accountable actor id missing");
        }
    }

    private static void stampReview(AnomalyFindingEntity finding, String reviewerActorId, String reason) {
        finding.setReviewedBy(reviewerActorId);
        finding.setReviewedAt(OffsetDateTime.now());
        finding.setReviewReason(reason);
    }
}
