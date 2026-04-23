package zw.gov.mohcc.impilo.varapi.core.biometric;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricProfileEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricTemplateEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricVerificationEventEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderBiometricProfileRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderBiometricTemplateRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderBiometricVerificationEventRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProviderBiometricService {

    private final ProviderRepository providerRepository;
    private final ProviderBiometricProfileRepository profileRepository;
    private final ProviderBiometricTemplateRepository templateRepository;
    private final ProviderBiometricPolicyClient policyClient;
    private final ProviderBiometricVerificationEventRepository verificationEventRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ProviderBiometricService(
            ProviderRepository providerRepository,
            ProviderBiometricProfileRepository profileRepository,
            ProviderBiometricTemplateRepository templateRepository,
            ProviderBiometricPolicyClient policyClient,
            ProviderBiometricVerificationEventRepository verificationEventRepository,
            EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.providerRepository = providerRepository;
        this.profileRepository = profileRepository;
        this.templateRepository = templateRepository;
        this.policyClient = policyClient;
        this.verificationEventRepository = verificationEventRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProviderBiometricTemplateEntity enroll(
            String providerPublicId,
            ProviderBiometricModality modality,
            String position,
            byte[] templateData,
            Integer qualityScore,
            String captureDevice,
            String workflowType,
            String contextType,
            String vendorEngine,
            boolean livenessSupported) {
        requireInternal();
        TrustContext ctx = TrustContextHolder.require();
        ProviderEntity provider = providerRepository
                .findByProviderPublicIdAndTenantId(providerPublicId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider for tenant"));

        String wf = workflowType == null || workflowType.isBlank() ? "PROVIDER_ONBOARDING" : workflowType;
        String cx = contextType == null || contextType.isBlank() ? "FACILITY" : contextType;

        TshepoBiometricPolicyResponse policy =
                policyClient.evaluate("PROVIDER", wf, cx, modality.name(), "ENROLL");
        if (policy == null
                || !policy.enrollmentAllowed()
                || "PROHIBITED".equalsIgnoreCase(policy.policyOutcome())) {
            throw new ProviderBiometricPolicyDeniedException("Provider biometric enrollment denied by Tshepo");
        }

        ProviderBiometricProfileEntity profile = ensureProfile(ctx.tenantId(), provider.getId(), ctx.actorId());

        Optional<ProviderBiometricTemplateEntity> existing =
                templateRepository.findByTenantIdAndProviderIdAndModalityAndPositionAndStatus(
                        ctx.tenantId(), provider.getId(), modality, position, "ACTIVE");
        existing.ifPresent(t -> {
            t.setStatus("SUPERSEDED");
            t.setSupersededAt(Instant.now());
            templateRepository.save(t);
        });

        ProviderBiometricTemplateEntity t = new ProviderBiometricTemplateEntity();
        t.setProfileId(profile.getProfileId());
        t.setTenantId(ctx.tenantId());
        t.setProviderId(provider.getId());
        t.setModality(modality);
        t.setPosition(position);
        t.setTemplateData(templateData);
        t.setTemplateHash(sha256Hex(templateData));
        t.setQualityScore(qualityScore);
        t.setCaptureDevice(captureDevice);
        t.setCapturedBy(ctx.actorId());
        t.setStatus("ACTIVE");
        t.setVendorEngine(vendorEngine);
        t.setLivenessSupported(livenessSupported);
        ProviderBiometricTemplateEntity saved = templateRepository.save(t);

        profile.setBiometricStatus("ENROLLED");
        profile.setAssuranceLevel(qualityScore);
        profileRepository.save(profile);

        publishOutbox(
                "PROVIDER",
                providerPublicId,
                "biometric.enrollment.created",
                Map.of(
                        "providerPublicId", providerPublicId,
                        "profileId", profile.getProfileId().toString(),
                        "modality", modality.name(),
                        "workflowType", wf,
                        "policyOutcome", policy.policyOutcome()));

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ProviderBiometricTemplateEntity> listActive(String providerPublicId) {
        requireInternal();
        TrustContext ctx = TrustContextHolder.require();
        ProviderEntity provider = providerRepository
                .findByProviderPublicIdAndTenantId(providerPublicId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider for tenant"));
        return templateRepository.findByTenantIdAndProviderIdAndStatus(ctx.tenantId(), provider.getId(), "ACTIVE");
    }

    @Transactional(readOnly = true)
    public Optional<ProviderBiometricProfileEntity> findProfile(String providerPublicId) {
        requireInternal();
        TrustContext ctx = TrustContextHolder.require();
        ProviderEntity provider = providerRepository
                .findByProviderPublicIdAndTenantId(providerPublicId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider for tenant"));
        return profileRepository.findByTenantIdAndProviderId(ctx.tenantId(), provider.getId());
    }

    @Transactional
    public Map<String, Object> verify(
            String providerPublicId,
            ProviderBiometricModality modality,
            byte[] probe,
            String workflowType,
            String contextType,
            String biometricIntent) {
        requireInternal();
        TrustContext ctx = TrustContextHolder.require();
        ProviderEntity provider = providerRepository
                .findByProviderPublicIdAndTenantId(providerPublicId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider for tenant"));

        String intent =
                biometricIntent == null || biometricIntent.isBlank() ? "VERIFY" : biometricIntent;
        String wf = workflowType == null || workflowType.isBlank() ? "PROVIDER_STEP_UP" : workflowType;
        String cx = contextType == null || contextType.isBlank() ? "FACILITY" : contextType;
        TshepoBiometricPolicyResponse policy =
                policyClient.evaluate("PROVIDER", wf, cx, modality.name(), intent);
        if (policy == null
                || !policy.verificationAllowed()
                || "PROHIBITED".equalsIgnoreCase(policy.policyOutcome())) {
            throw new ProviderBiometricPolicyDeniedException("Provider biometric verification denied by Tshepo");
        }

        List<ProviderBiometricTemplateEntity> templates =
                templateRepository.findByTenantIdAndProviderIdAndStatus(ctx.tenantId(), provider.getId(), "ACTIVE")
                        .stream()
                        .filter(t -> t.getModality() == modality)
                        .toList();
        if (templates.isEmpty()) {
            throw new IllegalStateException("No active provider biometric templates");
        }

        String probeHash = sha256Hex(probe);
        String result = "NO_MATCH";
        double confidence = 0.0;
        for (ProviderBiometricTemplateEntity t : templates) {
            if (probeHash.equals(t.getTemplateHash())) {
                result = "MATCH";
                confidence = 1.0;
                break;
            }
        }

        Optional<ProviderBiometricProfileEntity> profileOpt =
                profileRepository.findByTenantIdAndProviderId(ctx.tenantId(), provider.getId());
        ProviderBiometricVerificationEventEntity ev = new ProviderBiometricVerificationEventEntity();
        ev.setTenantId(ctx.tenantId());
        ev.setProviderId(provider.getId());
        ev.setProfileId(profileOpt.map(ProviderBiometricProfileEntity::getProfileId).orElse(null));
        ev.setWorkflowType(wf);
        ev.setModality(modality.name());
        ev.setRequestedBy(ctx.actorId());
        ev.setResult(result);
        ev.setConfidenceScore(confidence);
        ev.setLivenessOutcome("NOT_REPORTED");
        ev.setDecisionSummary("Template-hash equality (placeholder matcher)");
        ev.setPolicyRuleId(policy.matchedRuleId());
        ev.setPolicyOutcome(policy.policyOutcome());
        ProviderBiometricVerificationEventEntity saved = verificationEventRepository.save(ev);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", result);
        body.put("confidence", confidence);
        body.put("workflowType", wf);
        body.put("policyOutcome", policy.policyOutcome());
        body.put("verificationEventId", saved.getId());

        publishOutbox(
                "PROVIDER",
                providerPublicId,
                "biometric.verification.performed",
                body);

        return body;
    }

    private ProviderBiometricProfileEntity ensureProfile(UUID tenantId, Long providerId, String actorId) {
        return profileRepository
                .findByTenantIdAndProviderId(tenantId, providerId)
                .orElseGet(() -> {
                    ProviderBiometricProfileEntity p = new ProviderBiometricProfileEntity();
                    p.setProfileId(UUID.randomUUID());
                    p.setTenantId(tenantId);
                    p.setProviderId(providerId);
                    p.setBiometricStatus("NONE");
                    p.setActiveFlag(true);
                    p.setEnrolledBy(actorId);
                    return profileRepository.save(p);
                });
    }

    private static void requireInternal() {
        if (TrustContextHolder.require().mode() == AccessMode.EXTERNAL) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Provider biometric API is internal only");
        }
    }

    private void publishOutbox(String aggregateType, String aggregateId, String eventType, Map<String, ?> payload) {
        TrustContext ctx = TrustContextHolder.require();
        EventOutboxEntity e = new EventOutboxEntity();
        e.setAggregateType(aggregateType);
        e.setAggregateId(aggregateId);
        e.setEventType(eventType);
        try {
            e.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
        e.setTenantId(ctx.tenantId().toString());
        e.setCorrelationId(ctx.correlationId().toString());
        e.setSubjectType(aggregateType);
        e.setSubjectId(aggregateId);
        e.setProducer("varapi-service");
        e.setSchemaVersion(1);
        outboxRepository.save(e);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
