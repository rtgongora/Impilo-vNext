package zw.gov.mohcc.impilo.khuluma.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.khuluma.domain.PresenceEntity;
import zw.gov.mohcc.impilo.khuluma.repository.PresenceRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vashandi duty / on-call presence (Phase 5 / W7, G-KH-04). Duty status is orthogonal to the
 * availability status: a worker can be ON_DUTY or ON_CALL. The on-call roster (who can be paged for
 * an escalation) is the set of ON_CALL workers for a tenant.
 */
@Service
public class OnCallService {

    private static final Set<String> DUTY = Set.of("OFF_DUTY", "ON_DUTY", "ON_CALL");

    private final PresenceRepository presence;

    public OnCallService(PresenceRepository presence) {
        this.presence = presence;
    }

    /** Set a worker's duty status (upserts their presence row). */
    @Transactional
    public PresenceEntity setDuty(UUID tenantId, String actorId, String actorType, String dutyStatus) {
        String duty = normalize(dutyStatus);
        PresenceEntity p = presence.findByTenantIdAndActorId(tenantId, actorId).orElseGet(PresenceEntity::new);
        p.setTenantId(tenantId);
        p.setActorId(actorId);
        if (actorType != null) p.setActorType(actorType);
        p.setDutyStatus(duty);
        p.setUpdatedAt(OffsetDateTime.now());
        return presence.save(p);
    }

    /** The on-call roster for a tenant (ON_CALL workers). */
    @Transactional(readOnly = true)
    public List<PresenceEntity> onCallRoster(UUID tenantId) {
        return presence.findByTenantIdAndDutyStatusOrderByUpdatedAtDesc(tenantId, "ON_CALL");
    }

    private static String normalize(String dutyStatus) {
        if (dutyStatus == null) {
            throw new IllegalArgumentException("dutyStatus is required");
        }
        String d = dutyStatus.trim().toUpperCase();
        if (!DUTY.contains(d)) {
            throw new IllegalArgumentException("Unknown dutyStatus: " + dutyStatus);
        }
        return d;
    }
}
