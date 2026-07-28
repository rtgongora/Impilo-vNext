package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalPrehabItemEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalPrehabItemRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SB-1 — prehabilitation/optimisation execution items (surgical domain pack §10). The 16-domain
 * vocabulary is ENGINEERING-derived (see V005's own header — the source spec is not vendored in
 * this repository); a domain row records THIS SERVICE's execution status of an optimisation
 * workstream, never the underlying clinical fact (which stays in its own registry — e.g. smoking
 * status in pct_social_history — referenced via {@code registryRef}, not copied).
 */
@Service
public class PrehabItemService {

    private static final Set<String> DOMAINS = Set.of(
            "ANAEMIA", "NUTRITION", "GLYCAEMIC_CONTROL", "SMOKING_CESSATION", "ALCOHOL_REDUCTION",
            "VTE_PLAN", "ANTIBIOTIC_PROPHYLAXIS_PLAN", "BLOOD_PREPARATION",
            "CARDIORESPIRATORY_FITNESS", "PHYSIOTHERAPY", "PSYCHOLOGICAL_PREPARATION",
            "MEDICATION_OPTIMISATION", "DENTAL", "WEIGHT_MANAGEMENT", "FRAILTY_INTERVENTION",
            "SOCIAL_DISCHARGE_PREPARATION");
    private static final Set<String> STATUSES = Set.of(
            "PLANNED", "IN_PROGRESS", "COMPLETED", "NOT_REQUIRED", "DECLINED");

    private final SurgicalPrehabItemRepository repository;
    private final SurgicalEpisodeRepository episodeRepository;

    public PrehabItemService(SurgicalPrehabItemRepository repository, SurgicalEpisodeRepository episodeRepository) {
        this.repository = repository;
        this.episodeRepository = episodeRepository;
    }

    private UUID currentTenant() {
        return TrustContextHolder.require().tenantId();
    }

    private String currentActor() {
        try {
            String actor = TrustContextHolder.require().actorId();
            return actor != null && !actor.isBlank() ? actor : "system";
        } catch (IllegalStateException e) {
            return "system";
        }
    }

    @Transactional
    public Map<String, Object> recordItem(UUID episodeId, Map<String, Object> body) {
        UUID tenant = currentTenant();
        episodeRepository.findByIdAndTenantId(episodeId, tenant)
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_EPISODE_NOT_FOUND", 404,
                        "No surgical episode " + episodeId));

        String domain = body.get("domain") != null ? body.get("domain").toString() : null;
        if (domain == null || !DOMAINS.contains(domain)) {
            throw new SurgeryDomainException("INVALID_PREHAB_DOMAIN", 400, "domain must be one of " + DOMAINS);
        }

        SurgicalPrehabItemEntity e = repository
                .findBySurgicalEpisodeIdAndTenantIdAndDomain(episodeId, tenant, domain)
                .orElseGet(() -> {
                    SurgicalPrehabItemEntity fresh = new SurgicalPrehabItemEntity();
                    fresh.setTenantId(tenant);
                    fresh.setSurgicalEpisodeId(episodeId);
                    fresh.setDomain(domain);
                    fresh.setPlannedBy(currentActor());
                    return fresh;
                });

        if (body.get("status") != null) {
            String status = body.get("status").toString();
            if (!STATUSES.contains(status)) {
                throw new SurgeryDomainException("INVALID_PREHAB_STATUS", 400, "status must be one of " + STATUSES);
            }
            e.setStatus(status);
        }
        if (body.get("target") != null) e.setTarget(body.get("target").toString());
        if (body.get("notes") != null) e.setNotes(body.get("notes").toString());
        if (body.get("registryRef") != null) e.setRegistryRef(body.get("registryRef").toString());

        return toView(repository.save(e));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForEpisode(UUID episodeId) {
        return repository.findBySurgicalEpisodeIdAndTenantIdOrderByDomainAsc(episodeId, currentTenant())
                .stream().map(this::toView).toList();
    }

    private Map<String, Object> toView(SurgicalPrehabItemEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("surgicalEpisodeId", e.getSurgicalEpisodeId().toString());
        m.put("domain", e.getDomain());
        m.put("status", e.getStatus());
        m.put("target", e.getTarget());
        m.put("notes", e.getNotes());
        m.put("registryRef", e.getRegistryRef());
        m.put("plannedBy", e.getPlannedBy());
        return m;
    }
}
