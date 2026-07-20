package zw.gov.mohcc.impilo.abis.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine.IdentificationCandidate;
import zw.gov.mohcc.impilo.abis.persistence.entity.AdjudicationCaseEntity;
import zw.gov.mohcc.impilo.abis.persistence.entity.DuplicateInvestigationEntity;
import zw.gov.mohcc.impilo.abis.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;
import zw.gov.mohcc.impilo.abis.persistence.repository.AdjudicationCaseRepository;
import zw.gov.mohcc.impilo.abis.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.abis.persistence.repository.TemplateRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ABIS adjudication + duplicate-investigation platform (W3c).
 *
 * <p>1:N identification and enrol-time dedup NEVER auto-merge. Candidate matches OPEN a
 * human-adjudicated case; a reviewer assigns it, records a DISTINCT / CONFIRMED_DUPLICATE
 * decision, and — only for a confirmed duplicate — may perform a governed merge that
 * requires <b>dual control</b> (a second reviewer distinct from the decider). ABIS marks
 * the merged-away subject's templates {@code MERGED} and emits an event; the Health-ID-level
 * merge is TSHEPO's responsibility (ABIS never holds a Health ID).</p>
 */
@Service
public class AdjudicationService {

    public static final String TEMPLATE_STATUS_MERGED = "MERGED";

    private final AdjudicationCaseRepository caseRepository;
    private final TemplateRepository templateRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AdjudicationService(AdjudicationCaseRepository caseRepository,
                               TemplateRepository templateRepository,
                               EventOutboxRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.caseRepository = caseRepository;
        this.templateRepository = templateRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Open a duplicate-investigation case from candidate matches. Never merges — status OPEN,
     * awaiting human adjudication. Returns the persisted case.
     */
    @Transactional
    public AdjudicationCaseEntity openCase(UUID tenantId, String subjectRef, BiometricModality modality,
                                           String reason, List<IdentificationCandidate> candidates) {
        AdjudicationCaseEntity kase = new AdjudicationCaseEntity();
        kase.setTenantId(tenantId);
        kase.setSubjectRef(subjectRef);
        kase.setModality(modality.name());
        kase.setReason(reason);
        kase.setStatus(AdjudicationCaseEntity.STATUS_OPEN);
        kase.setCandidateCount(candidates.size());

        double topScore = 0.0;
        int rank = 0;
        for (IdentificationCandidate c : candidates) {
            DuplicateInvestigationEntity cand = new DuplicateInvestigationEntity();
            cand.setCandidateSubjectRef(c.subjectRef());
            cand.setScore(c.confidence());
            cand.setSummary(c.summary());
            cand.setRank(rank++);
            kase.addCandidate(cand);
            topScore = Math.max(topScore, c.confidence());
        }
        kase.setTopScore(candidates.isEmpty() ? null : topScore);

        AdjudicationCaseEntity saved = caseRepository.save(kase);

        Map<String, Object> payload = basePayload(tenantId, saved);
        payload.put("candidateCount", saved.getCandidateCount());
        payload.put("topScore", saved.getTopScore());
        recordEvent(saved.getId(), "abis.adjudication.case.opened", payload);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<AdjudicationCaseEntity> listQueue(UUID tenantId, String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return caseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }
        return caseRepository.findByTenantIdAndStatusOrderByCreatedAtAsc(
                tenantId, status.trim().toUpperCase(), pageable);
    }

    @Transactional(readOnly = true)
    public AdjudicationCaseEntity getCase(UUID tenantId, Long id) {
        AdjudicationCaseEntity kase = requireCase(tenantId, id);
        // Force candidate initialization inside the transaction.
        kase.getCandidates().size();
        return kase;
    }

    /** Assign a reviewer → status IN_REVIEW. */
    @Transactional
    public AdjudicationCaseEntity assignReviewer(UUID tenantId, Long id, String reviewer) {
        if (reviewer == null || reviewer.isBlank()) {
            throw new IllegalArgumentException("reviewer is required");
        }
        AdjudicationCaseEntity kase = requireCase(tenantId, id);
        if (AdjudicationCaseEntity.STATUS_RESOLVED.equals(kase.getStatus())) {
            throw new IllegalStateException("case is already RESOLVED");
        }
        kase.setAssignedReviewer(reviewer.trim());
        kase.setStatus(AdjudicationCaseEntity.STATUS_IN_REVIEW);
        AdjudicationCaseEntity saved = caseRepository.save(kase);

        Map<String, Object> payload = basePayload(tenantId, saved);
        payload.put("assignedReviewer", reviewer.trim());
        recordEvent(saved.getId(), "abis.adjudication.case.assigned", payload);
        return saved;
    }

    /**
     * Record a human decision → status RESOLVED. This NEVER merges — a confirmed duplicate
     * still requires the separate, dual-control {@link #merge} action.
     */
    @Transactional
    public AdjudicationCaseEntity recordDecision(UUID tenantId, Long id, String decision,
                                                 String reviewer, String reason) {
        if (reviewer == null || reviewer.isBlank()) {
            throw new IllegalArgumentException("reviewer (X-Actor-ID) is required to record a decision");
        }
        String normalized = decision == null ? "" : decision.trim().toUpperCase();
        if (!AdjudicationCaseEntity.DECISION_DISTINCT.equals(normalized)
                && !AdjudicationCaseEntity.DECISION_CONFIRMED_DUPLICATE.equals(normalized)) {
            throw new IllegalArgumentException(
                    "decision must be DISTINCT or CONFIRMED_DUPLICATE");
        }
        AdjudicationCaseEntity kase = requireCase(tenantId, id);
        if (AdjudicationCaseEntity.STATUS_RESOLVED.equals(kase.getStatus())) {
            throw new IllegalStateException("case is already RESOLVED");
        }
        kase.setDecision(normalized);
        kase.setDecidedBy(reviewer.trim());
        kase.setDecidedAt(Instant.now());
        kase.setDecisionReason(reason);
        kase.setStatus(AdjudicationCaseEntity.STATUS_RESOLVED);
        AdjudicationCaseEntity saved = caseRepository.save(kase);

        Map<String, Object> payload = basePayload(tenantId, saved);
        payload.put("decision", normalized);
        payload.put("decidedBy", reviewer.trim());
        recordEvent(saved.getId(), "abis.adjudication.decision.recorded", payload);
        return saved;
    }

    /**
     * Governed merge — NEVER automatic. Preconditions (all enforced, fail-closed):
     * <ul>
     *   <li>the case decision is {@code CONFIRMED_DUPLICATE};</li>
     *   <li>the case has not already been merged;</li>
     *   <li>the surviving subject is one of the case candidates;</li>
     *   <li><b>dual control</b>: the second reviewer is present and DISTINCT from the decider.</li>
     * </ul>
     * ABIS marks the merged-away subject's templates {@code MERGED} and emits
     * {@code abis.adjudication.merge.performed}; the Health-ID-level merge is TSHEPO's job.
     */
    @Transactional
    public AdjudicationCaseEntity merge(UUID tenantId, Long id, String mergeTargetSubjectRef,
                                        String secondReviewer) {
        if (secondReviewer == null || secondReviewer.isBlank()) {
            throw new IllegalArgumentException(
                    "a second reviewer (X-Actor-ID) is required — merge is dual-control");
        }
        if (mergeTargetSubjectRef == null || mergeTargetSubjectRef.isBlank()) {
            throw new IllegalArgumentException("mergeTargetSubjectRef (surviving subject) is required");
        }
        AdjudicationCaseEntity kase = requireCase(tenantId, id);

        if (!AdjudicationCaseEntity.DECISION_CONFIRMED_DUPLICATE.equals(kase.getDecision())) {
            throw new IllegalStateException(
                    "merge requires a CONFIRMED_DUPLICATE decision (record the decision first)");
        }
        if (kase.getMergedAt() != null) {
            throw new IllegalStateException("case has already been merged");
        }
        String survivor = mergeTargetSubjectRef.trim();
        boolean survivorIsCandidate = kase.getCandidates().stream()
                .anyMatch(c -> survivor.equals(c.getCandidateSubjectRef()));
        if (!survivorIsCandidate) {
            throw new IllegalArgumentException(
                    "mergeTargetSubjectRef must be one of the case candidates");
        }
        // DUAL CONTROL: the merge signer must differ from the decision recorder.
        if (secondReviewer.trim().equals(kase.getDecidedBy())) {
            throw new IllegalStateException(
                    "dual control violated: the merge reviewer must differ from the decision reviewer");
        }

        // Mark the merged-away (probe) subject's templates MERGED. The surviving subject
        // keeps its ACTIVE templates. ABIS never touches a Health ID.
        String mergedAwaySubject = kase.getSubjectRef();
        List<TemplateEntity> mergedTemplates =
                templateRepository.findByTenantIdAndSubjectRefAndStatus(
                        tenantId, mergedAwaySubject, AbisTemplateService.STATUS_ACTIVE);
        for (TemplateEntity t : mergedTemplates) {
            t.setStatus(TEMPLATE_STATUS_MERGED);
        }
        templateRepository.saveAll(mergedTemplates);

        kase.setMergeTargetSubjectRef(survivor);
        kase.setDualSignOffBy(secondReviewer.trim());
        kase.setMergedAt(Instant.now());
        AdjudicationCaseEntity saved = caseRepository.save(kase);

        Map<String, Object> payload = basePayload(tenantId, saved);
        payload.put("mergedSubjectRef", mergedAwaySubject);
        payload.put("survivingSubjectRef", survivor);
        payload.put("decidedBy", kase.getDecidedBy());
        payload.put("dualSignOffBy", secondReviewer.trim());
        payload.put("mergedTemplateCount", mergedTemplates.size());
        recordEvent(saved.getId(), "abis.adjudication.merge.performed", payload);
        return saved;
    }

    private AdjudicationCaseEntity requireCase(UUID tenantId, Long id) {
        return caseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("adjudication case not found: " + id));
    }

    private static Map<String, Object> basePayload(UUID tenantId, AdjudicationCaseEntity kase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("caseId", kase.getId());
        payload.put("subjectRef", kase.getSubjectRef());
        payload.put("modality", kase.getModality());
        payload.put("reason", kase.getReason());
        payload.put("status", kase.getStatus());
        payload.put("occurredAt", Instant.now().toString());
        return payload;
    }

    private void recordEvent(Long caseId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("abis.adjudication");
        event.setAggregateId(String.valueOf(caseId));
        event.setEventType(eventType);
        try {
            // Pre-serialized JSON string — StringSerializer estate law. No PII, no template bytes.
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, e);
        }
        outboxRepository.save(event);
    }
}
