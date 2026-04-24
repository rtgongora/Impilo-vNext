package zw.gov.mohcc.impilo.vito.core.biometric;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.core.BiometricModality;
import zw.gov.mohcc.impilo.vito.core.biometric.engine.BiometricMatchingEngine;
import zw.gov.mohcc.impilo.vito.core.biometric.engine.BiometricMatchingEngine.IdentificationCandidate;
import zw.gov.mohcc.impilo.vito.core.biometric.engine.BiometricMatchingEngine.VerificationDecision;
import zw.gov.mohcc.impilo.vito.core.biometric.engine.BiometricProbeContext;
import zw.gov.mohcc.impilo.vito.core.biometric.policy.BiometricPolicyClient;
import zw.gov.mohcc.impilo.vito.core.biometric.policy.TshepoBiometricPolicyResponse;
import zw.gov.mohcc.impilo.vito.persistence.entity.*;
import zw.gov.mohcc.impilo.vito.persistence.repository.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Policy-aware biometric orchestration for client subjects in VITO (enrollment, 1:1, 1:N, exceptions).
 */
@Service
public class BiometricOperationsService {

    private final BiometricService biometricService;
    private final BiometricTemplateRepository templateRepository;
    private final BiometricProfileRepository profileRepository;
    private final BiometricEnrollmentEventRepository enrollmentEventRepository;
    private final BiometricVerificationEventRepository verificationEventRepository;
    private final BiometricIdentificationEventRepository identificationEventRepository;
    private final BiometricExceptionRecordRepository exceptionRecordRepository;
    private final EventOutboxRepository outboxRepository;
    private final BiometricPolicyClient policyClient;
    private final BiometricMatchingEngine matchingEngine;
    private final ObjectMapper objectMapper;

    public BiometricOperationsService(
            BiometricService biometricService,
            BiometricTemplateRepository templateRepository,
            BiometricProfileRepository profileRepository,
            BiometricEnrollmentEventRepository enrollmentEventRepository,
            BiometricVerificationEventRepository verificationEventRepository,
            BiometricIdentificationEventRepository identificationEventRepository,
            BiometricExceptionRecordRepository exceptionRecordRepository,
            EventOutboxRepository outboxRepository,
            BiometricPolicyClient policyClient,
            BiometricMatchingEngine matchingEngine,
            ObjectMapper objectMapper) {
        this.biometricService = biometricService;
        this.templateRepository = templateRepository;
        this.profileRepository = profileRepository;
        this.enrollmentEventRepository = enrollmentEventRepository;
        this.verificationEventRepository = verificationEventRepository;
        this.identificationEventRepository = identificationEventRepository;
        this.exceptionRecordRepository = exceptionRecordRepository;
        this.outboxRepository = outboxRepository;
        this.policyClient = policyClient;
        this.matchingEngine = matchingEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BiometricTemplateEntity enrollGoverned(
            UUID healthId,
            BiometricModality modality,
            String position,
            byte[] templateData,
            Integer qualityScore,
            String captureDevice,
            String workflowType,
            String contextType,
            String consentRef,
            String authorizationRef,
            String vendorEngine,
            boolean livenessSupported) {
        TrustContext ctx = TrustContextHolder.require();
        TshepoBiometricPolicyResponse policy = requirePolicy(
                "CLIENT", workflowType, contextType, modality == null ? null : modality.name(), "ENROLL");
        if (!policy.enrollmentAllowed() || "PROHIBITED".equalsIgnoreCase(policy.policyOutcome())) {
            throw new BiometricPolicyDeniedException("Biometric enrollment denied by Tshepo policy");
        }

        BiometricProfileEntity profile = ensureProfile(ctx.tenantId(), healthId, contextType, ctx.actorId());
        BiometricTemplateEntity template = biometricService.enroll(
                ctx.tenantId(), healthId, modality, position, templateData, qualityScore, captureDevice, ctx.actorId());
        template.setProfileId(profile.getProfileId());
        template.setVendorEngine(vendorEngine);
        template.setLivenessSupported(livenessSupported);
        templateRepository.save(template);

        profile.setBiometricStatus("ENROLLED");
        profile.setAssuranceLevel(qualityScore);
        profileRepository.save(profile);

        BiometricEnrollmentEventEntity ev = new BiometricEnrollmentEventEntity();
        ev.setProfileId(profile.getProfileId());
        ev.setSubjectType("CLIENT");
        ev.setHealthId(healthId);
        ev.setWorkflowType(workflowType);
        ev.setEnrolledBy(ctx.actorId());
        ev.setEnrollmentContext(contextType);
        ev.setQualityOutcome(qualityScore == null ? "UNKNOWN" : "SCORE_" + qualityScore);
        ev.setConsentRef(consentRef);
        ev.setAuthorizationRef(authorizationRef);
        ev.setPolicyRuleId(policy.matchedRuleId());
        ev.setPolicyOutcome(policy.policyOutcome());
        enrollmentEventRepository.save(ev);

        Map<String, Object> enrollPayload = new LinkedHashMap<>();
        enrollPayload.put("healthId", healthId.toString());
        enrollPayload.put("profileId", profile.getProfileId().toString());
        enrollPayload.put("modality", modality.name());
        enrollPayload.put("workflowType", workflowType);
        enrollPayload.put("contextType", contextType);
        enrollPayload.put("policyOutcome", policy.policyOutcome());
        if (policy.matchedRuleId() != null) {
            enrollPayload.put("matchedRuleId", policy.matchedRuleId());
        }
        publishOutbox("CLIENT", healthId.toString(), "biometric.enrollment.created", enrollPayload);

        return template;
    }

    @Transactional
    public BiometricVerificationEventEntity verifyGoverned(
            UUID healthId,
            BiometricModality modality,
            byte[] probeTemplate,
            String workflowType,
            String contextType,
            String biometricIntent,
            BiometricProbeContext probeContext) {
        TrustContext ctx = TrustContextHolder.require();
        String intent = biometricIntent == null || biometricIntent.isBlank() ? "VERIFY" : biometricIntent;
        TshepoBiometricPolicyResponse policy = requirePolicy(
                "CLIENT", workflowType, contextType, modality == null ? null : modality.name(), intent);
        if (!policy.verificationAllowed() || "PROHIBITED".equalsIgnoreCase(policy.policyOutcome())) {
            throw new BiometricPolicyDeniedException("Biometric verification denied by Tshepo policy");
        }

        List<BiometricTemplateEntity> templates =
                templateRepository.findByTenantIdAndHealthIdAndStatus(ctx.tenantId(), healthId, "ACTIVE");
        if (modality != null) {
            templates = templates.stream().filter(t -> t.getModality() == modality).toList();
        }
        if (templates.isEmpty()) {
            throw new IllegalStateException("No active biometric templates for client");
        }

        BiometricProbeContext probeCtx =
                probeContext != null ? probeContext : BiometricProbeContext.EMPTY;
        VerificationDecision best = new VerificationDecision("NO_MATCH", 0.0, "No template matched");
        for (BiometricTemplateEntity t : templates) {
            VerificationDecision d = matchingEngine.verify(probeTemplate, t, probeCtx);
            if ("MATCH".equals(d.result())) {
                best = d;
                break;
            }
            best = d;
        }

        Optional<BiometricProfileEntity> profile = profileRepository.findByTenantIdAndHealthId(ctx.tenantId(), healthId);

        BiometricVerificationEventEntity ev = new BiometricVerificationEventEntity();
        ev.setProfileId(profile.map(BiometricProfileEntity::getProfileId).orElse(null));
        ev.setHealthId(healthId);
        ev.setWorkflowType(workflowType);
        ev.setRequestedBy(ctx.actorId());
        if (probeCtx.livenessScore() != null) {
            ev.setLivenessOutcome("SCORE_" + probeCtx.livenessScore());
        } else {
            ev.setLivenessOutcome("NOT_REPORTED");
        }
        ev.setResult(best.result());
        ev.setConfidenceScore(best.confidence());
        ev.setDecisionSummary(best.summary());
        ev.setPolicyRuleId(policy.matchedRuleId());
        ev.setPolicyOutcome(policy.policyOutcome());
        verificationEventRepository.save(ev);

        publishOutbox(
                "CLIENT",
                healthId.toString(),
                "biometric.verification.performed",
                Map.of(
                        "healthId", healthId.toString(),
                        "result", best.result(),
                        "workflowType", workflowType,
                        "contextType", contextType,
                        "policyOutcome", policy.policyOutcome()));

        return ev;
    }

    @Transactional
    public BiometricIdentificationEventEntity identifyGoverned(
            BiometricModality modality,
            byte[] probeTemplate,
            String workflowType,
            String contextType) {
        TrustContext ctx = TrustContextHolder.require();
        TshepoBiometricPolicyResponse policy = requirePolicy(
                "CLIENT", workflowType, contextType, modality == null ? null : modality.name(), "IDENTIFY");
        if (!policy.identificationAllowed() || "PROHIBITED".equalsIgnoreCase(policy.policyOutcome())) {
            throw new BiometricPolicyDeniedException("Biometric identification denied by Tshepo policy");
        }

        List<BiometricTemplateEntity> candidates =
                templateRepository.findByTenantIdAndModalityAndStatus(ctx.tenantId(), modality, "ACTIVE");
        List<IdentificationCandidate> hits =
                matchingEngine.identify(probeTemplate, candidates, BiometricProbeContext.EMPTY);

        BiometricIdentificationEventEntity ev = new BiometricIdentificationEventEntity();
        ev.setSubjectType("CLIENT");
        ev.setWorkflowType(workflowType);
        ev.setRequestedBy(ctx.actorId());
        ev.setCandidateCount(candidates.size());
        if (!hits.isEmpty()) {
            ev.setTopMatchHealthId(hits.getFirst().healthId());
            ev.setConfidenceScore(hits.getFirst().confidence());
            ev.setResult("CANDIDATES");
            ev.setDecisionSummary(hits.getFirst().summary());
        } else {
            ev.setResult("NO_HIT");
            ev.setDecisionSummary("No digest-level candidate (replace engine for production 1:N)");
        }
        ev.setPolicyRuleId(policy.matchedRuleId());
        ev.setPolicyOutcome(policy.policyOutcome());
        identificationEventRepository.save(ev);

        publishOutbox(
                "CLIENT",
                ctx.tenantId().toString(),
                "biometric.identification.performed",
                Map.of(
                        "tenantId", ctx.tenantId().toString(),
                        "candidateCount", candidates.size(),
                        "hitCount", hits.size(),
                        "workflowType", workflowType,
                        "contextType", contextType,
                        "policyOutcome", policy.policyOutcome()));

        return ev;
    }

    @Transactional
    public BiometricIdentificationEventEntity assistDeduplicationGoverned(
            BiometricModality modality,
            byte[] probeTemplate,
            String workflowType,
            String contextType) {
        TrustContext ctx = TrustContextHolder.require();
        TshepoBiometricPolicyResponse policy = requirePolicy(
                "CLIENT", workflowType, contextType, modality == null ? null : modality.name(), "DEDUP_SUPPORT");
        if (!policy.dedupSupportAllowed() || "PROHIBITED".equalsIgnoreCase(policy.policyOutcome())) {
            throw new BiometricPolicyDeniedException("Biometric deduplication assist denied by Tshepo policy");
        }
        List<BiometricTemplateEntity> candidates =
                templateRepository.findByTenantIdAndModalityAndStatus(ctx.tenantId(), modality, "ACTIVE");
        List<IdentificationCandidate> hits =
                matchingEngine.identify(probeTemplate, candidates, BiometricProbeContext.EMPTY);

        BiometricIdentificationEventEntity ev = new BiometricIdentificationEventEntity();
        ev.setSubjectType("CLIENT");
        ev.setWorkflowType(workflowType);
        ev.setRequestedBy(ctx.actorId());
        ev.setCandidateCount(candidates.size());
        if (!hits.isEmpty()) {
            ev.setTopMatchHealthId(hits.getFirst().healthId());
            ev.setConfidenceScore(hits.getFirst().confidence());
            ev.setResult("DEDUP_CANDIDATE");
            ev.setDecisionSummary(hits.getFirst().summary());
        } else {
            ev.setResult("NO_DEDUP_SIGNAL");
            ev.setDecisionSummary("No digest-level duplicate signal");
        }
        ev.setPolicyRuleId(policy.matchedRuleId());
        ev.setPolicyOutcome(policy.policyOutcome());
        identificationEventRepository.save(ev);

        publishOutbox(
                "CLIENT",
                ctx.tenantId().toString(),
                "biometric.match.candidate_generated",
                Map.of(
                        "tenantId", ctx.tenantId().toString(),
                        "workflowType", workflowType,
                        "contextType", contextType,
                        "hitCount", hits.size()));

        return ev;
    }

    @Transactional
    public BiometricExceptionRecordEntity recordException(
            UUID healthId,
            String workflowType,
            String exceptionType,
            String reason,
            boolean fallbackUsed) {
        TrustContext ctx = TrustContextHolder.require();
        BiometricExceptionRecordEntity ex = new BiometricExceptionRecordEntity();
        ex.setSubjectType("CLIENT");
        ex.setHealthId(healthId);
        ex.setWorkflowType(workflowType);
        ex.setExceptionType(exceptionType);
        ex.setReason(reason);
        ex.setFallbackUsed(fallbackUsed);
        ex.setCreatedBy(ctx.actorId());
        BiometricExceptionRecordEntity saved = exceptionRecordRepository.save(ex);

        publishOutbox(
                "CLIENT",
                healthId == null ? ctx.tenantId().toString() : healthId.toString(),
                "biometric.exception.recorded",
                Map.of(
                        "exceptionType", exceptionType,
                        "workflowType", workflowType,
                        "fallbackUsed", fallbackUsed));

        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<BiometricProfileEntity> findProfile(UUID tenantId, UUID healthId) {
        return profileRepository.findByTenantIdAndHealthId(tenantId, healthId);
    }

    private TshepoBiometricPolicyResponse requirePolicy(
            String subjectType, String workflowType, String contextType, String modality, String intent) {
        TshepoBiometricPolicyResponse policy =
                policyClient.evaluate(subjectType, workflowType, contextType, modality, intent);
        if (policy == null) {
            throw new IllegalStateException("Tshepo returned empty biometric policy payload");
        }
        return policy;
    }

    private BiometricProfileEntity ensureProfile(UUID tenantId, UUID healthId, String sourceContext, String actorId) {
        return profileRepository
                .findByTenantIdAndHealthId(tenantId, healthId)
                .orElseGet(() -> {
                    BiometricProfileEntity p = new BiometricProfileEntity();
                    p.setProfileId(UUID.randomUUID());
                    p.setTenantId(tenantId);
                    p.setHealthId(healthId);
                    p.setSubjectType("CLIENT");
                    p.setBiometricStatus("NONE");
                    p.setActiveFlag(true);
                    p.setSourceContext(sourceContext);
                    p.setEnrolledBy(actorId);
                    return profileRepository.save(p);
                });
    }

    private void publishOutbox(String aggregateType, String aggregateId, String eventType, Map<String, ?> payload) {
        TrustContext ctx = TrustContextHolder.require();
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize biometric outbox payload", e);
        }
        event.setTenantId(ctx.tenantId().toString());
        event.setCorrelationId(ctx.correlationId().toString());
        event.setSubjectType(aggregateType);
        event.setSubjectId(aggregateId);
        event.setProducer("vito-service");
        event.setSchemaVersion(1);
        outboxRepository.save(event);
    }
}
