package zw.gov.mohcc.impilo.learning.fundo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningProviderEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningSpaceApplicationEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ProviderAccreditationEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningProviderRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningSpaceApplicationRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.ProviderAccreditationRepository;

/**
 * Learning-provider request → review → accredit → provision workflow (C2). On
 * accreditation it provisions a {@link LearningProviderEntity} and records an
 * accreditation routed to the regulator matching the provider kind:
 * INDIVIDUAL→professional council, ORGANISATION→organisational accreditation,
 * FACILITY→facility registration. Emits {@code provider.accredited.v1}.
 */
@Service
public class FundoAccreditationService {

    private final LearningSpaceApplicationRepository applicationRepository;
    private final ProviderAccreditationRepository accreditationRepository;
    private final LearningProviderRepository providerRepository;
    private final FundoLearningProviderService providerService;
    private final FundoOutboxAppender outbox;
    private final ObjectMapper objectMapper;

    public FundoAccreditationService(
            LearningSpaceApplicationRepository applicationRepository,
            ProviderAccreditationRepository accreditationRepository,
            LearningProviderRepository providerRepository,
            FundoLearningProviderService providerService,
            FundoOutboxAppender outbox,
            ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.accreditationRepository = accreditationRepository;
        this.providerRepository = providerRepository;
        this.providerService = providerService;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> submit(UUID tenantId, Map<String, Object> body) {
        String kind = reqStr(body, "providerKind").toUpperCase();
        if (!FundoLearningProviderService.PROVIDER_KINDS.contains(kind)) {
            throw new IllegalArgumentException("providerKind must be one of " + FundoLearningProviderService.PROVIDER_KINDS);
        }
        LearningSpaceApplicationEntity a = new LearningSpaceApplicationEntity();
        a.setTenantId(tenantId);
        a.setProviderKind(kind);
        a.setRequesterSubjectType(nullable(body, "requesterSubjectType"));
        a.setRequesterSubjectId(nullable(body, "requesterSubjectId"));
        a.setRequestedName(reqStr(body, "requestedName"));
        a.setTargetIdentityRef(nullable(body, "targetIdentityRef"));
        a.setDisciplinesJson(writeJson(body.get("disciplines")));
        a.setStatus("SUBMITTED");
        applicationRepository.save(a);
        return applicationView(a);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listApplications(UUID tenantId, String status, int limit) {
        List<LearningSpaceApplicationEntity> rows = (status == null || status.isBlank())
                ? applicationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, limit))
                : applicationRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, PageRequest.of(0, limit));
        return Map.of("items", rows.stream().map(FundoAccreditationService::applicationView).toList(), "limit", limit);
    }

    @Transactional
    public Map<String, Object> decide(UUID tenantId, String applicationId, String actorId, Map<String, Object> body) {
        LearningSpaceApplicationEntity a = application(tenantId, applicationId);
        a.setStatus(reqStr(body, "status"));
        a.setDecisionBy(actorId);
        a.setDecisionAt(OffsetDateTime.now());
        a.setDecisionNotes(nullable(body, "decisionNotes"));
        a.setUpdatedAt(OffsetDateTime.now());
        applicationRepository.save(a);
        return applicationView(a);
    }

    /** Accredit the application: provision the provider, record accreditation, emit the event. */
    @Transactional
    public Map<String, Object> accredit(UUID tenantId, String applicationId, String actorId, Map<String, Object> body) {
        LearningSpaceApplicationEntity a = application(tenantId, applicationId);

        Map<String, Object> providerBody = new LinkedHashMap<>();
        providerBody.put("providerKind", a.getProviderKind());
        providerBody.put("displayName", a.getRequestedName());
        providerBody.put("sorRef", a.getTargetIdentityRef());
        if ("INDIVIDUAL".equals(a.getProviderKind())) {
            providerBody.put("councilRef", nullable(body, "councilRef"));
        }
        Map<String, Object> provider = providerService.createProvider(tenantId, actorId, providerBody);
        UUID providerId = UUID.fromString(provider.get("id").toString());

        String regulator = str(body, "regulator", regulatorForKind(a.getProviderKind()));
        ProviderAccreditationEntity acc = new ProviderAccreditationEntity();
        acc.setTenantId(tenantId);
        acc.setProviderId(providerId);
        acc.setRegulator(regulator);
        acc.setCertificateRef(nullable(body, "certificateRef"));
        acc.setValidUntil(timeOrNull(body.get("validUntil")));
        acc.setStatus("ACTIVE");
        acc.setAccreditedBy(actorId);
        accreditationRepository.save(acc);

        providerService.setAccreditationStatus(tenantId, providerId, "ACCREDITED");

        a.setStatus("ACCREDITED");
        a.setDecisionBy(actorId);
        a.setDecisionAt(OffsetDateTime.now());
        a.setProvisionedProviderId(providerId);
        a.setUpdatedAt(OffsetDateTime.now());
        applicationRepository.save(a);

        outbox.append("learning_provider", providerId.toString(), FundoNativeEventTypes.PROVIDER_ACCREDITED, Map.of(
                "tenantId", tenantId.toString(),
                "providerId", providerId.toString(),
                "providerKind", a.getProviderKind(),
                "regulator", regulator,
                "applicationId", a.getId().toString()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("application", applicationView(a));
        // Re-read so the view reflects the ACCREDITED status just applied.
        out.put("provider", providerService.getProvider(tenantId, providerId.toString()));
        out.put("regulator", regulator);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listAccreditations(UUID tenantId, String providerId) {
        List<Map<String, Object>> items = accreditationRepository
                .findByTenantIdAndProviderIdOrderByAccreditedAtDesc(tenantId, UUID.fromString(providerId))
                .stream().map(FundoAccreditationService::accreditationView).toList();
        return Map.of("providerId", providerId, "items", items);
    }

    private static String regulatorForKind(String kind) {
        return switch (kind) {
            case "INDIVIDUAL" -> "PROFESSIONAL_COUNCIL";   // varapi council
            case "FACILITY" -> "FACILITY_REGULATOR";       // tuso / facility registration
            default -> "ORGANISATIONAL_ACCREDITATION";     // workforce-governance + regulator
        };
    }

    private LearningSpaceApplicationEntity application(UUID tenantId, String applicationId) {
        return applicationRepository.findByTenantIdAndId(tenantId, UUID.fromString(applicationId))
                .orElseThrow(() -> new IllegalArgumentException("application not found"));
    }

    private static Map<String, Object> applicationView(LearningSpaceApplicationEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("providerKind", a.getProviderKind());
        m.put("requestedName", a.getRequestedName());
        m.put("targetIdentityRef", a.getTargetIdentityRef());
        m.put("status", a.getStatus());
        m.put("decisionNotes", a.getDecisionNotes());
        m.put("provisionedProviderId", a.getProvisionedProviderId() == null ? null : a.getProvisionedProviderId().toString());
        return m;
    }

    private static Map<String, Object> accreditationView(ProviderAccreditationEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("regulator", a.getRegulator());
        m.put("certificateRef", a.getCertificateRef());
        m.put("validUntil", a.getValidUntil() == null ? null : a.getValidUntil().toString());
        m.put("status", a.getStatus());
        m.put("accreditedAt", a.getAccreditedAt() == null ? null : a.getAccreditedAt().toString());
        return m;
    }

    private String writeJson(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private static OffsetDateTime timeOrNull(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        return OffsetDateTime.parse(o.toString());
    }

    private static String reqStr(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key, String def) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? def : v.toString();
    }

    private static String nullable(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }
}
