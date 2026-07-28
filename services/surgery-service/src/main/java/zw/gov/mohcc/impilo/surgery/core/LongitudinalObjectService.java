package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalLongitudinalObjectEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalLongitudinalObjectRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SB-2 — drains, stomas and wounds as longitudinal objects (surgical domain pack §17). Implants
 * stay federated to inventory-service's own registry (P8) — see V006's own header; this class
 * never touches implant data.
 */
@Service
public class LongitudinalObjectService {

    private static final Set<String> KINDS = Set.of("DRAIN", "STOMA", "WOUND");

    private final SurgicalLongitudinalObjectRepository repository;
    private final SurgicalEpisodeRepository episodeRepository;

    public LongitudinalObjectService(SurgicalLongitudinalObjectRepository repository,
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
    public Map<String, Object> place(UUID episodeId, Map<String, Object> body) {
        UUID tenant = currentTenant();
        episodeRepository.findByIdAndTenantId(episodeId, tenant)
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_EPISODE_NOT_FOUND", 404,
                        "No surgical episode " + episodeId));

        String kind = body.get("kind") != null ? body.get("kind").toString() : null;
        if (kind == null || !KINDS.contains(kind)) {
            throw new SurgeryDomainException("INVALID_LONGITUDINAL_OBJECT_KIND", 400, "kind must be one of " + KINDS);
        }
        String site = body.get("site") != null ? body.get("site").toString() : null;
        String indication = body.get("indication") != null ? body.get("indication").toString() : null;
        if (site == null || site.isBlank() || indication == null || indication.isBlank()) {
            throw new SurgeryDomainException("SITE_AND_INDICATION_REQUIRED", 400,
                    "site and indication are both required");
        }

        SurgicalLongitudinalObjectEntity e = new SurgicalLongitudinalObjectEntity();
        e.setTenantId(tenant);
        e.setSurgicalEpisodeId(episodeId);
        e.setKind(kind);
        e.setSite(site);
        e.setIndication(indication);
        e.setOperator(body.get("operator") != null ? body.get("operator").toString() : currentActor());
        e.setStatus("ACTIVE");
        e.setPlacedAt(OffsetDateTime.now());
        return toView(repository.save(e));
    }

    @Transactional
    public Map<String, Object> remove(UUID objectId, Map<String, Object> body) {
        SurgicalLongitudinalObjectEntity e = requireObject(objectId);
        e.setStatus("REMOVED");
        e.setRemovedAt(OffsetDateTime.now());
        e.setRemovedBy(currentActor());
        if (body.get("removalReason") != null) e.setRemovalReason(body.get("removalReason").toString());
        return toView(repository.save(e));
    }

    /**
     * A device is replaced by a newer one — same idiom as inventory-service's own implant
     * revision (P8). The OLD object is marked REVISED and points forward; the NEW object is
     * created fresh.
     */
    @Transactional
    public Map<String, Object> revise(UUID oldObjectId, Map<String, Object> body) {
        SurgicalLongitudinalObjectEntity old = requireObject(oldObjectId);

        SurgicalLongitudinalObjectEntity fresh = new SurgicalLongitudinalObjectEntity();
        fresh.setTenantId(old.getTenantId());
        fresh.setSurgicalEpisodeId(old.getSurgicalEpisodeId());
        fresh.setKind(old.getKind());
        fresh.setSite(body.get("site") != null ? body.get("site").toString() : old.getSite());
        fresh.setIndication(body.get("indication") != null ? body.get("indication").toString()
                : "Revision of " + old.getId());
        fresh.setOperator(body.get("operator") != null ? body.get("operator").toString() : currentActor());
        fresh.setStatus("ACTIVE");
        fresh.setPlacedAt(OffsetDateTime.now());
        fresh = repository.save(fresh);

        old.setStatus("REVISED");
        old.setRemovedAt(OffsetDateTime.now());
        old.setRemovedBy(currentActor());
        old.setRevisedToObjectId(fresh.getId());
        if (body.get("removalReason") != null) old.setRemovalReason(body.get("removalReason").toString());
        repository.save(old);

        return toView(fresh);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForEpisode(UUID episodeId) {
        return repository.findBySurgicalEpisodeIdAndTenantIdOrderByPlacedAtDesc(episodeId, currentTenant())
                .stream().map(this::toView).toList();
    }

    private SurgicalLongitudinalObjectEntity requireObject(UUID objectId) {
        return repository.findByIdAndTenantId(objectId, currentTenant())
                .orElseThrow(() -> new SurgeryDomainException("LONGITUDINAL_OBJECT_NOT_FOUND", 404,
                        "No longitudinal object " + objectId));
    }

    private Map<String, Object> toView(SurgicalLongitudinalObjectEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("surgicalEpisodeId", e.getSurgicalEpisodeId().toString());
        m.put("kind", e.getKind());
        m.put("site", e.getSite());
        m.put("placedAt", e.getPlacedAt().toString());
        m.put("operator", e.getOperator());
        m.put("indication", e.getIndication());
        m.put("status", e.getStatus());
        m.put("removedAt", e.getRemovedAt() != null ? e.getRemovedAt().toString() : null);
        m.put("removedBy", e.getRemovedBy());
        m.put("removalReason", e.getRemovalReason());
        m.put("revisedToObjectId", e.getRevisedToObjectId() != null ? e.getRevisedToObjectId().toString() : null);
        return m;
    }
}
