package zw.gov.mohcc.impilo.abis.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.abis.config.AbisThresholdProperties;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine.IdentificationCandidate;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine.VerificationDecision;
import zw.gov.mohcc.impilo.abis.engine.BiometricProbeContext;
import zw.gov.mohcc.impilo.abis.persistence.entity.AdjudicationCaseEntity;
import zw.gov.mohcc.impilo.abis.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;
import zw.gov.mohcc.impilo.abis.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.abis.persistence.repository.TemplateRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ABIS template custody + matching orchestration.
 *
 * <ul>
 *   <li><b>Enroll</b> — upserts the encrypted template row for
 *       (tenant, subject_ref, modality, position); records {@code abis.template.enrolled}.</li>
 *   <li><b>Verify (1:1)</b> — routine after a candidate is identified; delegates to the
 *       {@link BiometricMatchingEngine} (fail-closed default returns {@code UNAVAILABLE});
 *       records {@code abis.verify.performed}.</li>
 *   <li><b>Identify (1:N)</b> — restricted to ENROLMENT / RECOVERY / DEDUPLICATION and returns
 *       candidates for human adjudication, NEVER an automatic merge; records
 *       {@code abis.identify.performed}.</li>
 * </ul>
 *
 * <p>Outbox payloads are pre-serialized JSON strings (Kafka value serializer is
 * StringSerializer — estate law). No PII and no template bytes ever enter events.</p>
 */
@Service
public class AbisTemplateService {

    public static final String DEFAULT_POSITION = "PRIMARY";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private final TemplateRepository templateRepository;
    private final EventOutboxRepository outboxRepository;
    private final BiometricMatchingEngine matchingEngine;
    private final TemplateCrypto templateCrypto;
    private final ObjectMapper objectMapper;
    private final AdjudicationService adjudicationService;
    private final AbisThresholdProperties thresholds;
    private final int identifyMaxCandidates;

    public AbisTemplateService(TemplateRepository templateRepository,
                               EventOutboxRepository outboxRepository,
                               BiometricMatchingEngine matchingEngine,
                               TemplateCrypto templateCrypto,
                               ObjectMapper objectMapper,
                               AdjudicationService adjudicationService,
                               AbisThresholdProperties thresholds,
                               @Value("${abis.identify.max-candidates:500}") int identifyMaxCandidates) {
        this.templateRepository = templateRepository;
        this.outboxRepository = outboxRepository;
        this.matchingEngine = matchingEngine;
        this.templateCrypto = templateCrypto;
        this.objectMapper = objectMapper;
        this.adjudicationService = adjudicationService;
        this.thresholds = thresholds;
        this.identifyMaxCandidates = identifyMaxCandidates;
    }

    /**
     * Outcome of an enrol: the persisted template plus, when enrol-time dedup (W3b) trips,
     * the duplicate-investigation case that was OPENED for human adjudication (never a merge).
     */
    public record EnrollOutcome(TemplateEntity template, AdjudicationCaseEntity duplicateCase) {
        public boolean hasDuplicateReview() {
            return duplicateCase != null;
        }
    }

    @Transactional
    public EnrollOutcome enroll(UUID tenantId, String subjectRef, BiometricModality modality,
                                String position, Integer qualityScore, String algorithmVersion,
                                byte[] templateBytes) {
        String pos = normalizePosition(position);

        // W3d — quality gating: reject a capture below the governed per-modality minimum
        // (fail-closed; 0 = disabled). A degraded reference must never enter the gallery.
        int minQuality = thresholds.minQuality(modality.name());
        if (minQuality > 0 && qualityScore != null && qualityScore < minQuality) {
            throw new QualityBelowThresholdException(qualityScore, minQuality);
        }

        // W3b — enrol-time dedup: bounded 1:N against the tenant gallery (excluding this
        // subject's own prior templates). Candidates at/above the dedup threshold OPEN a
        // duplicate-investigation case. Never auto-merge; enrolment still proceeds (care-first).
        AdjudicationCaseEntity duplicateCase =
                runEnrolDedup(tenantId, subjectRef, modality, templateBytes);

        // W3d — versioning: supersede the prior ACTIVE row (keep history), insert version+1.
        TemplateEntity prior = templateRepository
                .findByTenantIdAndSubjectRefAndModalityAndPositionAndStatus(
                        tenantId, subjectRef, modality, pos, STATUS_ACTIVE)
                .orElse(null);
        int nextVersion = 1;
        if (prior != null) {
            prior.setStatus(STATUS_SUPERSEDED);
            templateRepository.save(prior);
            nextVersion = prior.getVersion() + 1;
        }

        TemplateEntity entity = new TemplateEntity();
        entity.setTenantId(tenantId);
        entity.setSubjectRef(subjectRef);
        entity.setModality(modality);
        entity.setPosition(pos);
        entity.setQualityScore(qualityScore);
        entity.setAlgorithmVersion(algorithmVersion);
        entity.setTemplateEncrypted(templateCrypto.encrypt(templateBytes));
        entity.setStatus(STATUS_ACTIVE);
        entity.setVersion(nextVersion);
        TemplateEntity saved = templateRepository.save(entity);

        Map<String, Object> payload = basePayload(tenantId, subjectRef, modality, pos);
        payload.put("qualityScore", qualityScore);
        payload.put("algorithmVersion", algorithmVersion);
        payload.put("version", nextVersion);
        if (duplicateCase != null) {
            payload.put("duplicateCaseId", duplicateCase.getId());
        }
        recordEvent("abis.template", subjectRef, "abis.template.enrolled", payload);
        return new EnrollOutcome(saved, duplicateCase);
    }

    /**
     * Enrol-time 1:N dedup. Returns the OPENED duplicate-investigation case, or null when
     * dedup is disabled or no candidate reaches the threshold. NEVER merges.
     */
    private AdjudicationCaseEntity runEnrolDedup(UUID tenantId, String subjectRef,
                                                 BiometricModality modality, byte[] templateBytes) {
        if (!thresholds.getDedup().isEnabled()) {
            return null;
        }
        List<TemplateEntity> gallery = templateRepository.findByTenantIdAndModalityAndStatus(
                tenantId, modality, STATUS_ACTIVE,
                PageRequest.of(0, thresholds.getDedup().getMaxGallery()));
        // Exclude this subject's own templates — re-enrolling yourself is not a duplicate.
        List<TemplateEntity> others = gallery.stream()
                .filter(t -> !subjectRef.equals(t.getSubjectRef()))
                .toList();
        if (others.isEmpty()) {
            return null;
        }
        List<IdentificationCandidate> candidates =
                matchingEngine.identify(templateBytes, others, BiometricProbeContext.EMPTY);
        double threshold = thresholds.getDedup().getThreshold();
        List<IdentificationCandidate> hits = candidates.stream()
                .filter(c -> c.confidence() >= threshold)
                .toList();
        if (hits.isEmpty()) {
            return null;
        }
        // Reason ENROLMENT — a human adjudicates; the enrol still lands (care-first).
        return adjudicationService.openCase(tenantId, subjectRef, modality, "ENROLMENT", hits);
    }

    /**
     * Extract a template from a raw capture image via the matching engine
     * (enrolment-from-image). Fail-closed when no engine/model is available.
     */
    public BiometricMatchingEngine.ExtractionResult extractTemplate(
            BiometricModality modality, byte[] sampleImage, int width, int height, int dpi) {
        return matchingEngine.extractTemplate(modality, sampleImage, width, height, dpi);
    }

    @Transactional
    public VerificationDecision verify(UUID tenantId, String subjectRef, BiometricModality modality,
                                       String position, byte[] probeTemplate,
                                       BiometricProbeContext probeContext) {
        TemplateEntity enrolled = (position == null || position.isBlank()
                ? templateRepository.findFirstByTenantIdAndSubjectRefAndModalityAndStatusOrderByUpdatedAtDesc(
                        tenantId, subjectRef, modality, STATUS_ACTIVE)
                : templateRepository.findByTenantIdAndSubjectRefAndModalityAndPositionAndStatus(
                        tenantId, subjectRef, modality, normalizePosition(position), STATUS_ACTIVE))
                .orElse(null);

        VerificationDecision decision = (enrolled == null)
                ? new VerificationDecision("NO_REFERENCE", 0.0,
                        "No enrolled template for subject_ref/modality — verification fails closed")
                : matchingEngine.verify(probeTemplate, enrolled, safeContext(probeContext));

        Map<String, Object> payload = basePayload(tenantId, subjectRef, modality, position);
        payload.put("decision", decision.result());
        payload.put("confidence", decision.confidence());
        recordEvent("abis.verify", subjectRef, "abis.verify.performed", payload);
        return decision;
    }

    /**
     * 1:N identification. Returns candidates for adjudication — never an automatic merge.
     * The identify reason has already been validated at the API boundary.
     */
    @Transactional
    public List<IdentificationCandidate> identify(UUID tenantId, BiometricModality modality,
                                                  byte[] probeTemplate, String reason,
                                                  BiometricProbeContext probeContext) {
        List<TemplateEntity> candidates = templateRepository.findByTenantIdAndModalityAndStatus(
                tenantId, modality, STATUS_ACTIVE, PageRequest.of(0, identifyMaxCandidates));
        List<IdentificationCandidate> result =
                matchingEngine.identify(probeTemplate, candidates, safeContext(probeContext));

        Map<String, Object> payload = basePayload(tenantId, null, modality, null);
        payload.put("reason", reason);
        payload.put("candidateCount", result.size());
        payload.put("decision", "CANDIDATES_FOR_ADJUDICATION");
        recordEvent("abis.identify", tenantId.toString(), "abis.identify.performed", payload);
        return result;
    }

    private static String normalizePosition(String position) {
        return (position == null || position.isBlank()) ? DEFAULT_POSITION : position.trim();
    }

    private static BiometricProbeContext safeContext(BiometricProbeContext probeContext) {
        return probeContext == null ? BiometricProbeContext.EMPTY : probeContext;
    }

    private static Map<String, Object> basePayload(UUID tenantId, String subjectRef,
                                                   BiometricModality modality, String position) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        if (subjectRef != null) {
            payload.put("subjectRef", subjectRef);
        }
        payload.put("modality", modality.name());
        if (position != null && !position.isBlank()) {
            payload.put("position", position);
        }
        payload.put("occurredAt", Instant.now().toString());
        return payload;
    }

    private void recordEvent(String aggregateType, String aggregateId, String eventType,
                             Map<String, Object> payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        try {
            // Pre-serialized JSON string — the outbox publisher sends it verbatim (StringSerializer).
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, e);
        }
        outboxRepository.save(event);
    }
}
