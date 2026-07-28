package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalWaitlistRevalidationEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalWaitlistRevalidationRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SB-2 — waiting-list clinical revalidation state (surgical domain pack §9). Surgery-service's
 * ONLY slice of the 19-field waiting-list model — the rest is scheduling-service's own
 * {@code surgical_waitlist_entry}, untouched by this class. Append-only: each revalidation is a
 * dated, attributed event.
 */
@Service
public class WaitlistRevalidationService {

    private final SurgicalWaitlistRevalidationRepository repository;
    private final SurgicalEpisodeRepository episodeRepository;

    public WaitlistRevalidationService(SurgicalWaitlistRevalidationRepository repository,
                                       SurgicalEpisodeRepository episodeRepository) {
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
    public Map<String, Object> revalidate(UUID episodeId, Map<String, Object> body) {
        UUID tenant = currentTenant();
        episodeRepository.findByIdAndTenantId(episodeId, tenant)
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_EPISODE_NOT_FOUND", 404,
                        "No surgical episode " + episodeId));

        if (body.get("stillClinicallyAppropriate") == null) {
            throw new SurgeryDomainException("STILL_CLINICALLY_APPROPRIATE_REQUIRED", 400,
                    "stillClinicallyAppropriate is required — an explicit yes/no, never assumed");
        }

        SurgicalWaitlistRevalidationEntity e = new SurgicalWaitlistRevalidationEntity();
        e.setTenantId(tenant);
        e.setSurgicalEpisodeId(episodeId);
        e.setRevalidatedBy(currentActor());
        e.setRevalidatedAt(java.time.OffsetDateTime.now());
        e.setStillClinicallyAppropriate(Boolean.parseBoolean(body.get("stillClinicallyAppropriate").toString()));
        if (body.get("notes") != null) e.setNotes(body.get("notes").toString());
        if (body.get("nextRevalidationDue") != null) {
            e.setNextRevalidationDue(LocalDate.parse(body.get("nextRevalidationDue").toString()));
        }
        return toView(repository.save(e));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForEpisode(UUID episodeId) {
        return repository.findBySurgicalEpisodeIdAndTenantIdOrderByRevalidatedAtDesc(episodeId, currentTenant())
                .stream().map(this::toView).toList();
    }

    private Map<String, Object> toView(SurgicalWaitlistRevalidationEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("surgicalEpisodeId", e.getSurgicalEpisodeId().toString());
        m.put("revalidatedAt", e.getRevalidatedAt().toString());
        m.put("revalidatedBy", e.getRevalidatedBy());
        m.put("stillClinicallyAppropriate", e.isStillClinicallyAppropriate());
        m.put("notes", e.getNotes());
        m.put("nextRevalidationDue", e.getNextRevalidationDue() != null ? e.getNextRevalidationDue().toString() : null);
        return m;
    }
}
