package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalFollowupEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalFollowupRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SB-2 — discharge/follow-up items (surgical domain pack §18): restrictions, fit note,
 * transport, future-surgery intent. One row per episode, refined not versioned (same S2/S3
 * idiom). {@code surveillancePlanRef} is a reference into {@code pct_care_plans} (PCT's, CC-2) —
 * never a copy of the plan itself.
 */
@Service
public class SurgicalFollowupService {

    private final SurgicalFollowupRepository repository;
    private final SurgicalEpisodeRepository episodeRepository;

    public SurgicalFollowupService(SurgicalFollowupRepository repository, SurgicalEpisodeRepository episodeRepository) {
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
    public Map<String, Object> record(UUID episodeId, Map<String, Object> body) {
        UUID tenant = currentTenant();
        episodeRepository.findByIdAndTenantId(episodeId, tenant)
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_EPISODE_NOT_FOUND", 404,
                        "No surgical episode " + episodeId));

        SurgicalFollowupEntity e = repository.findBySurgicalEpisodeIdAndTenantId(episodeId, tenant)
                .orElseGet(() -> {
                    SurgicalFollowupEntity fresh = new SurgicalFollowupEntity();
                    fresh.setTenantId(tenant);
                    fresh.setSurgicalEpisodeId(episodeId);
                    return fresh;
                });

        if (body.get("restrictions") != null) e.setRestrictions(body.get("restrictions").toString());
        if (body.get("transportNotes") != null) e.setTransportNotes(body.get("transportNotes").toString());
        if (body.get("futureSurgeryIntent") != null) e.setFutureSurgeryIntent(body.get("futureSurgeryIntent").toString());
        if (body.get("transportArranged") != null) {
            e.setTransportArranged(Boolean.parseBoolean(body.get("transportArranged").toString()));
        }
        if (body.get("surveillancePlanRef") != null) {
            e.setSurveillancePlanRef(UUID.fromString(body.get("surveillancePlanRef").toString()));
        }
        if (body.get("fitNoteNotes") != null) {
            e.setFitNoteNotes(body.get("fitNoteNotes").toString());
            e.setFitNoteIssuedAt(OffsetDateTime.now());
            e.setFitNoteIssuedBy(currentActor());
        }
        e.setRecordedBy(currentActor());
        e.setRecordedAt(OffsetDateTime.now());

        return toView(repository.save(e));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID episodeId) {
        SurgicalFollowupEntity e = repository.findBySurgicalEpisodeIdAndTenantId(episodeId, currentTenant())
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_FOLLOWUP_NOT_FOUND", 404,
                        "No follow-up record for episode " + episodeId));
        return toView(e);
    }

    private Map<String, Object> toView(SurgicalFollowupEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("surgicalEpisodeId", e.getSurgicalEpisodeId().toString());
        m.put("restrictions", e.getRestrictions());
        m.put("fitNoteNotes", e.getFitNoteNotes());
        m.put("fitNoteIssuedAt", e.getFitNoteIssuedAt() != null ? e.getFitNoteIssuedAt().toString() : null);
        m.put("fitNoteIssuedBy", e.getFitNoteIssuedBy());
        m.put("transportArranged", e.isTransportArranged());
        m.put("transportNotes", e.getTransportNotes());
        m.put("futureSurgeryIntent", e.getFutureSurgeryIntent());
        m.put("surveillancePlanRef", e.getSurveillancePlanRef() != null ? e.getSurveillancePlanRef().toString() : null);
        m.put("recordedBy", e.getRecordedBy());
        m.put("recordedAt", e.getRecordedAt() != null ? e.getRecordedAt().toString() : null);
        return m;
    }
}
