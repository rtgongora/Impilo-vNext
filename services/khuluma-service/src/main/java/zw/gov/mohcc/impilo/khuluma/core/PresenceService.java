package zw.gov.mohcc.impilo.khuluma.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.khuluma.domain.PresenceEntity;
import zw.gov.mohcc.impilo.khuluma.repository.PresenceRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns presence: an actor's current availability status. Status changes upsert the
 * presence row, append a domain/audit event, and push {@code presence.changed} so
 * watchers update live. Status values are clamped to the known set.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final String AGGREGATE = "Presence";
    private static final Set<String> VALID = Set.of("ONLINE", "AWAY", "BUSY", "DND", "OFFLINE");

    private final PresenceRepository presence;
    private final OutboxAppender outbox;
    private final RealtimeDispatcher realtime;

    public PresenceService(PresenceRepository presence, OutboxAppender outbox, RealtimeDispatcher realtime) {
        this.presence = presence;
        this.outbox = outbox;
        this.realtime = realtime;
    }

    @Transactional
    public PresenceEntity update(ActorContext ctx, String status, String statusMessage, String device) {
        String safeStatus = clamp(status);
        OffsetDateTime now = OffsetDateTime.now();

        PresenceEntity entity = presence.findByTenantIdAndActorId(ctx.tenantId(), ctx.actorId())
                .orElseGet(PresenceEntity::new);
        entity.setTenantId(ctx.tenantId());
        entity.setActorId(ctx.actorId());
        entity.setActorType(ctx.actorType());
        entity.setStatus(safeStatus);
        entity.setStatusMessage(statusMessage);
        if (device != null) {
            entity.setDevice(device);
        }
        entity.setLastSeenAt(now);
        if (!"OFFLINE".equals(safeStatus)) {
            entity.setLastActiveAt(now);
        }
        entity.setUpdatedAt(now);
        presence.save(entity);

        Map<String, Object> payload = presencePayload(entity);
        outbox.append("impilo.khuluma.presence.changed.v1", AGGREGATE, ctx.actorId(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "presence-" + ctx.actorId() + "-" + now.toInstant().toEpochMilli(), payload,
                ctx.actorId(), ctx.actorId(), "Actor");

        realtime.publish(ctx.tenantId(), "actor:" + ctx.actorId(), "presence.changed", payload);

        log.debug("Presence updated [actor={}, status={}]", ctx.actorId(), safeStatus);
        return entity;
    }

    @Transactional(readOnly = true)
    public PresenceEntity get(UUID tenantId, String actorId) {
        return presence.findByTenantIdAndActorId(tenantId, actorId).orElseGet(() -> {
            PresenceEntity offline = new PresenceEntity();
            offline.setTenantId(tenantId);
            offline.setActorId(actorId);
            offline.setStatus("OFFLINE");
            return offline;
        });
    }

    @Transactional(readOnly = true)
    public List<PresenceEntity> getMany(UUID tenantId, Collection<String> actorIds) {
        return presence.findByTenantIdAndActorIdIn(tenantId, actorIds);
    }

    private static Map<String, Object> presencePayload(PresenceEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actor_id", entity.getActorId());
        payload.put("status", entity.getStatus());
        payload.put("status_message", entity.getStatusMessage());
        payload.put("last_seen_at", entity.getLastSeenAt() != null ? entity.getLastSeenAt().toString() : null);
        return payload;
    }

    private static String clamp(String status) {
        if (status == null) return "OFFLINE";
        String upper = status.trim().toUpperCase();
        return VALID.contains(upper) ? upper : "OFFLINE";
    }
}
