package zw.gov.mohcc.impilo.learning.fundo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningProviderEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningSpaceEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningProviderRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningSpaceRepository;

/**
 * Learning-provider & academy registry (C1). Providers are bindings to identities owned
 * by other SoRs, discriminated by {@code provider_kind}; spaces bind to governance
 * OrganisationUnits. Fundo forks no identity/tenancy registry.
 */
@Service
public class FundoLearningProviderService {

    static final Set<String> PROVIDER_KINDS = Set.of("INDIVIDUAL", "ORGANISATION", "FACILITY");

    private final LearningProviderRepository providerRepository;
    private final LearningSpaceRepository spaceRepository;
    private final ObjectMapper objectMapper;

    public FundoLearningProviderService(
            LearningProviderRepository providerRepository,
            LearningSpaceRepository spaceRepository,
            ObjectMapper objectMapper) {
        this.providerRepository = providerRepository;
        this.spaceRepository = spaceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createProvider(UUID tenantId, String actorId, Map<String, Object> body) {
        String kind = reqStr(body, "providerKind").toUpperCase();
        if (!PROVIDER_KINDS.contains(kind)) {
            throw new IllegalArgumentException("providerKind must be one of " + PROVIDER_KINDS);
        }
        LearningProviderEntity p = new LearningProviderEntity();
        p.setTenantId(tenantId);
        p.setProviderKind(kind);
        p.setDisplayName(reqStr(body, "displayName"));
        p.setSorRef(nullable(body, "sorRef"));
        p.setCouncilRef(nullable(body, "councilRef"));
        p.setDisciplinesJson(writeJson(body.get("disciplines")));
        p.setBrandingRef(nullable(body, "brandingRef"));
        p.setAccreditationStatus("UNACCREDITED");
        p.setStatus("ACTIVE");
        p.setCreatedBy(actorId);
        providerRepository.save(p);
        return providerView(p);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listProviders(UUID tenantId, String kind, int limit) {
        List<LearningProviderEntity> rows = (kind == null || kind.isBlank())
                ? providerRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, limit))
                : providerRepository.findByTenantIdAndProviderKindOrderByCreatedAtDesc(tenantId, kind.toUpperCase(), PageRequest.of(0, limit));
        return Map.of("items", rows.stream().map(FundoLearningProviderService::providerView).toList(), "limit", limit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProvider(UUID tenantId, String providerId) {
        return providerRepository.findByTenantIdAndId(tenantId, UUID.fromString(providerId))
                .map(FundoLearningProviderService::providerView)
                .orElseThrow(() -> new IllegalArgumentException("provider not found"));
    }

    @Transactional
    public Map<String, Object> createSpace(UUID tenantId, String providerId, String actorId, Map<String, Object> body) {
        LearningProviderEntity provider = providerRepository.findByTenantIdAndId(tenantId, UUID.fromString(providerId))
                .orElseThrow(() -> new IllegalArgumentException("provider not found"));
        LearningSpaceEntity s = new LearningSpaceEntity();
        s.setTenantId(tenantId);
        s.setProviderId(provider.getId());
        s.setOrgUnitRef(nullable(body, "orgUnitRef"));
        s.setName(reqStr(body, "name"));
        s.setSpaceKind(str(body, "spaceKind", "ACADEMY"));
        s.setDisciplinesJson(writeJson(body.get("disciplines")));
        s.setStatus("ACTIVE");
        s.setCreatedBy(actorId);
        spaceRepository.save(s);
        return spaceView(s);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listSpaces(UUID tenantId, String providerId) {
        List<LearningSpaceEntity> rows = providerRepository.findByTenantIdAndId(tenantId, UUID.fromString(providerId))
                .map(p -> spaceRepository.findByTenantIdAndProviderIdOrderByCreatedAtDesc(tenantId, p.getId()))
                .orElseThrow(() -> new IllegalArgumentException("provider not found"));
        return Map.of("providerId", providerId, "items", rows.stream().map(FundoLearningProviderService::spaceView).toList());
    }

    /** Used by the accreditation workflow (C2) to flip a provider's accreditation status. */
    @Transactional
    public void setAccreditationStatus(UUID tenantId, UUID providerId, String status) {
        LearningProviderEntity p = providerRepository.findByTenantIdAndId(tenantId, providerId)
                .orElseThrow(() -> new IllegalArgumentException("provider not found"));
        p.setAccreditationStatus(status);
        p.setUpdatedAt(java.time.OffsetDateTime.now());
        providerRepository.save(p);
    }

    private static Map<String, Object> providerView(LearningProviderEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("providerKind", p.getProviderKind());
        m.put("displayName", p.getDisplayName());
        m.put("sorRef", p.getSorRef());
        m.put("councilRef", p.getCouncilRef());
        m.put("brandingRef", p.getBrandingRef());
        m.put("accreditationStatus", p.getAccreditationStatus());
        m.put("status", p.getStatus());
        return m;
    }

    private static Map<String, Object> spaceView(LearningSpaceEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("providerId", s.getProviderId().toString());
        m.put("orgUnitRef", s.getOrgUnitRef());
        m.put("name", s.getName());
        m.put("spaceKind", s.getSpaceKind());
        m.put("status", s.getStatus());
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
