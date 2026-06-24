package zw.gov.mohcc.impilo.datagovernance.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.datagovernance.domain.PrivacyDisplayPreferenceEntity;
import zw.gov.mohcc.impilo.datagovernance.repository.PrivacyDisplayPreferenceRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns per-user privacy display preferences for the Privacy-by-Architecture plane (§6).
 * Persists the user's PII-display posture and emits an update event. Invalid inputs are
 * clamped to safe values rather than rejected, so a preference is never left more
 * permissive than intended.
 */
@Service
public class PrivacyPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyPreferenceService.class);

    static final String DEFAULT_LEVEL = "PARTIAL";
    static final int DEFAULT_LOCK_MINUTES = 5;
    private static final Set<String> VALID_LEVELS = Set.of("FULL", "PARTIAL", "MINIMAL");
    private static final int MIN_LOCK = 1;
    private static final int MAX_LOCK = 60;
    private static final String AGGREGATE = "PrivacyDisplayPreference";

    private final PrivacyDisplayPreferenceRepository repository;
    private final PrivacyOutboxAppender outbox;

    public PrivacyPreferenceService(PrivacyDisplayPreferenceRepository repository,
                                    PrivacyOutboxAppender outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    static String clampLevel(String level) {
        if (level == null) return DEFAULT_LEVEL;
        String upper = level.trim().toUpperCase();
        return VALID_LEVELS.contains(upper) ? upper : DEFAULT_LEVEL;
    }

    static int clampMinutes(Integer minutes) {
        if (minutes == null) return DEFAULT_LOCK_MINUTES;
        if (minutes < MIN_LOCK || minutes > MAX_LOCK) return DEFAULT_LOCK_MINUTES;
        return minutes;
    }

    /** The persisted preference for the user, or empty when the user has never set one. */
    @Transactional(readOnly = true)
    public Optional<PrivacyDisplayPreferenceEntity> find(String userId, UUID tenantId) {
        return repository.findByTenantIdAndUserId(tenantId, userId);
    }

    /** Insert or update the user's preference and emit an updated event. */
    @Transactional
    public PrivacyDisplayPreferenceEntity upsert(String userId, String level, Integer minutes,
                                                 Boolean screenShare, UUID tenantId, String podId,
                                                 String correlationId, String idempotencyKey) {
        String safeLevel = clampLevel(level);
        int safeMinutes = clampMinutes(minutes);
        boolean safeScreenShare = screenShare == null || screenShare;

        PrivacyDisplayPreferenceEntity pref = repository
                .findByTenantIdAndUserId(tenantId, userId)
                .map(existing -> {
                    existing.setDefaultLevel(safeLevel);
                    existing.setAutoLockMinutes(safeMinutes);
                    existing.setScreenShareMode(safeScreenShare);
                    existing.setUpdatedAt(OffsetDateTime.now());
                    return existing;
                })
                .orElseGet(() -> new PrivacyDisplayPreferenceEntity(
                        tenantId, userId, safeLevel, safeMinutes, safeScreenShare));
        repository.save(pref);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("default_level", safeLevel);
        payload.put("auto_lock_minutes", safeMinutes);
        payload.put("screen_share_mode", safeScreenShare);

        outbox.append("impilo.data.governance.privacy-preference.updated.v1",
                AGGREGATE, pref.getId().toString(),
                tenantId, podId, correlationId,
                idempotencyKey != null ? idempotencyKey : "privpref-" + pref.getId(),
                payload, userId, userId, "User");

        log.info("Upserted privacy display preference [user={}, level={}, lock={}m, screenShare={}]",
                userId, safeLevel, safeMinutes, safeScreenShare);
        return pref;
    }
}
