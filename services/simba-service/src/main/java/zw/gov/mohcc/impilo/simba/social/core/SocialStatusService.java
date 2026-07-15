package zw.gov.mohcc.impilo.simba.social.core;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.core.WellnessAuditService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialStatusEntity;
import zw.gov.mohcc.impilo.simba.social.repository.SocialStatusRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ephemeral wellness-social statuses. A status is a short, expiring note with a mood + visibility.
 * <ul>
 *   <li><b>create</b> — sanitised (never clinical values), persisted, outbox-emitted, audited.</li>
 *   <li><b>listActive</b> — non-expired VISIBLE statuses, visibility-filtered through the central
 *       {@link SocialVisibilityService} (statuses reuse the post visibility ladder via a transient
 *       shim — PRIVATE→owner, FOLLOWER→owner-until-a-follow-graph, PUBLIC_WITHIN_IMPILO→everyone).</li>
 *   <li><b>delete</b> — owner soft-hides their own status (moderation → HIDDEN).</li>
 *   <li><b>expireOverdue</b> — the scheduled sweep flips past-expiry statuses to HIDDEN.</li>
 * </ul>
 */
@Service
public class SocialStatusService {

    private static final int DEFAULT_TTL_HOURS = 24;
    private static final int MAX_TTL_HOURS = 24 * 7;

    private final SocialStatusRepository repository;
    private final SocialVisibilityService visibility;
    private final SimbaEventEmitter events;
    private final WellnessAuditService audit;

    public SocialStatusService(SocialStatusRepository repository,
                               SocialVisibilityService visibility,
                               SimbaEventEmitter events,
                               WellnessAuditService audit) {
        this.repository = repository;
        this.visibility = visibility;
        this.events = events;
        this.audit = audit;
    }

    public record CreateStatus(String actorCpid, String actorType, String text, String mood,
                               String visibility, Integer ttlHours) { }

    @Transactional
    public SocialStatusEntity create(UUID tenantId, CreateStatus in, String correlationId, String requestId) {
        if (in.text() == null || in.text().isBlank()) {
            throw new IllegalArgumentException("Status text is required");
        }
        // Statuses must never carry clinical values (same guard as milestone posts).
        MilestoneSanitiser.requireSafe(in.text());

        int ttl = in.ttlHours() == null ? DEFAULT_TTL_HOURS : Math.max(1, Math.min(in.ttlHours(), MAX_TTL_HOURS));

        SocialStatusEntity s = new SocialStatusEntity();
        s.setTenantId(tenantId);
        s.setActorCpid(in.actorCpid());
        if (in.actorType() != null && !in.actorType().isBlank()) {
            s.setActorType(in.actorType());
        }
        s.setText(in.text().trim());
        s.setMood(in.mood());
        if (in.visibility() != null && !in.visibility().isBlank()) {
            s.setVisibility(in.visibility());
        }
        s.setExpiresAt(OffsetDateTime.now().plusHours(ttl));
        s = repository.save(s);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status_id", s.getStatusId().toString());
        payload.put("actor_cpid", s.getActorCpid());
        payload.put("visibility", s.getVisibility());
        payload.put("expires_at", s.getExpiresAt().toString());
        events.emit(tenantId, "WELLNESS_SOCIAL_STATUS", s.getStatusId().toString(),
                "impilo.simba.wellness.social.status.created.v1", s.getActorCpid(),
                "simba:social-status:" + s.getStatusId(), payload);

        audit.recordSelf(tenantId, in.actorCpid(), in.actorType(), in.actorCpid(),
                "wellness.social.status.create", correlationId, requestId);
        return s;
    }

    @Transactional(readOnly = true)
    public List<SocialStatusEntity> listActive(UUID tenantId, String viewerCpid, int limit) {
        int cap = Math.max(1, Math.min(limit, 100));
        List<SocialStatusEntity> active =
                repository.findActive(tenantId, OffsetDateTime.now(), PageRequest.of(0, cap * 2));
        return active.stream()
                .filter(s -> visibility.canView(asPost(s), viewer(tenantId, viewerCpid, s)))
                .limit(cap)
                .toList();
    }

    @Transactional
    public SocialStatusEntity delete(UUID tenantId, UUID statusId, String actorCpid,
                                     String correlationId, String requestId) {
        SocialStatusEntity s = repository.findByStatusIdAndTenantId(statusId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Status not found"));
        if (!s.getActorCpid().equals(actorCpid)) {
            throw new SecurityException("Only the author may remove their status");
        }
        s.setModerationStatus("HIDDEN");
        s = repository.save(s);

        audit.recordSelf(tenantId, actorCpid, s.getActorType(), actorCpid,
                "wellness.social.status.delete", correlationId, requestId);
        return s;
    }

    /** Scheduled sweep: flip past-expiry VISIBLE statuses to HIDDEN. Returns the number hidden. */
    @Transactional
    public int expireOverdue(int batchLimit) {
        List<SocialStatusEntity> expired =
                repository.findExpiredVisible(OffsetDateTime.now(), PageRequest.of(0, Math.max(1, batchLimit)));
        for (SocialStatusEntity s : expired) {
            s.setModerationStatus("HIDDEN");
        }
        repository.saveAll(expired);
        return expired.size();
    }

    /** Adapt a status onto the post visibility ladder (no sensitive category / group on statuses). */
    private static SocialPostEntity asPost(SocialStatusEntity s) {
        SocialPostEntity shim = new SocialPostEntity();
        shim.setActorCpid(s.getActorCpid());
        shim.setVisibility(s.getVisibility());
        shim.setModerationStatus(s.getModerationStatus());
        return shim;
    }

    private static SocialVisibilityService.ViewerContext viewer(UUID tenantId, String viewerCpid,
                                                                SocialStatusEntity s) {
        boolean owner = viewerCpid != null && viewerCpid.equals(s.getActorCpid());
        return new SocialVisibilityService.ViewerContext(
                tenantId, viewerCpid, owner, false, false, false, false, false, false);
    }
}
