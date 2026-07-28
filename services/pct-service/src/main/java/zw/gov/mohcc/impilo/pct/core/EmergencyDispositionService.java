package zw.gov.mohcc.impilo.pct.core;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import zw.gov.mohcc.impilo.pct.domain.EmergencyEpisodeState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyDispositionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyDispositionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;

/**
 * The episode-level disposition record (pct V208, W9b) — R12's third mandatory-content gate. See
 * V208's migration header for why this coexists with {@code ed_disposition} rather than replacing it.
 *
 * <p>The database CHECKs (chk_ed_disp_*) are the real guarantee; the application layer mirrors the
 * same rules here for a clear 400 instead of an opaque constraint-violation 500, following this
 * pack's established convention.
 *
 * <p><b>Only some disposition types drive the episode FSM.</b> DISCHARGED_HOME,
 * DISCHARGED_WITH_FOLLOWUP, REFERRED_OUTPATIENT, DIED, LEFT_BEFORE_ASSESSMENT,
 * LEFT_AGAINST_ADVICE, ABSCONDED and DECLINED_CARE have no other mechanism that ever closes the
 * episode, so recording one of these transitions it through {@link EmergencyEpisodeService#transition}
 * — the same tested state-machine path every other closure uses. ADMITTED_WARD, ADMITTED_ICU,
 * ADMITTED_HDU, TO_THEATRE, TO_MENTAL_HEALTH and TRANSFERRED_OUT do NOT — those close the episode
 * only through the acceptance handshake (V204), because a disposition record is emergency's own
 * documentation of a destination, not the accepting party's own write. Recording ADMITTED_WARD here
 * without a corresponding accepted handover leaves the episode OPEN_AWAITING_ACCEPTANCE (or
 * OPEN_IN_CARE if no handover was ever requested) — a deliberate gap the handshake exists to close,
 * not a bug in this service. OBSERVATION never transitions the episode at all — the patient is still
 * emergency's, tracked separately by {@link EmergencyObservationStayService}.
 */
@Service
public class EmergencyDispositionService {

    private static final Set<String> VALID_TYPES = Set.of(
            "DISCHARGED_HOME", "DISCHARGED_WITH_FOLLOWUP", "REFERRED_OUTPATIENT",
            "ADMITTED_WARD", "ADMITTED_ICU", "ADMITTED_HDU",
            "TO_THEATRE", "TRANSFERRED_OUT", "TO_MENTAL_HEALTH", "OBSERVATION",
            "DIED", "LEFT_BEFORE_ASSESSMENT", "LEFT_AGAINST_ADVICE", "ABSCONDED", "DECLINED_CARE");

    private static final Set<String> DESTINATION_REQUIRED = Set.of(
            "ADMITTED_WARD", "ADMITTED_ICU", "ADMITTED_HDU", "TO_THEATRE",
            "TRANSFERRED_OUT", "TO_MENTAL_HEALTH", "REFERRED_OUTPATIENT");

    private static final Set<String> LAST_SEEN_REQUIRED = Set.of(
            "LEFT_BEFORE_ASSESSMENT", "LEFT_AGAINST_ADVICE", "ABSCONDED");

    /** The only types with no other closure mechanism — see the class javadoc. */
    private static final Map<String, EmergencyEpisodeState> TERMINATES_EPISODE_AS = Map.ofEntries(
            Map.entry("DISCHARGED_HOME", EmergencyEpisodeState.CLOSED_DISCHARGED),
            Map.entry("DISCHARGED_WITH_FOLLOWUP", EmergencyEpisodeState.CLOSED_DISCHARGED),
            Map.entry("REFERRED_OUTPATIENT", EmergencyEpisodeState.CLOSED_DISCHARGED),
            Map.entry("DIED", EmergencyEpisodeState.CLOSED_DIED),
            Map.entry("LEFT_BEFORE_ASSESSMENT", EmergencyEpisodeState.CLOSED_LEFT_BEFORE_ASSESSMENT),
            Map.entry("LEFT_AGAINST_ADVICE", EmergencyEpisodeState.CLOSED_LEFT_AGAINST_ADVICE),
            Map.entry("ABSCONDED", EmergencyEpisodeState.CLOSED_ABSCONDED),
            Map.entry("DECLINED_CARE", EmergencyEpisodeState.CLOSED_DECLINED_CARE));

    private final EmergencyEpisodeRepository episodeRepository;
    private final EmergencyDispositionRepository dispositionRepository;
    private final EmergencyEpisodeService episodeService;

    public EmergencyDispositionService(EmergencyEpisodeRepository episodeRepository,
                                       EmergencyDispositionRepository dispositionRepository,
                                       EmergencyEpisodeService episodeService) {
        this.episodeRepository = episodeRepository;
        this.dispositionRepository = dispositionRepository;
        this.episodeService = episodeService;
    }

    @Transactional
    public EmergencyDispositionEntity record(UUID episodeId, UUID tenantId, Map<String, Object> body) {
        episodeRepository.findByEpisodeIdAndTenantId(episodeId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergency episode not found"));

        String dispositionType = str(body, "dispositionType", "disposition_type");
        if (dispositionType == null || !VALID_TYPES.contains(dispositionType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "disposition_type must be one of " + VALID_TYPES + ", got: " + dispositionType);
        }

        String disposedBy = str(body, "disposedBy", "disposed_by");
        String reason = str(body, "dispositionReason", "disposition_reason", "reason");
        if (disposedBy == null || disposedBy.isBlank() || reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "disposed_by and disposition_reason are both required — every disposition states who, when and why");
        }

        String destination = str(body, "destination");
        if (DESTINATION_REQUIRED.contains(dispositionType) && (destination == null || destination.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "destination is required for disposition_type " + dispositionType);
        }

        String causeOrCircumstances = str(body, "causeOrCircumstances", "cause_or_circumstances");
        if ("DIED".equals(dispositionType) && (causeOrCircumstances == null || causeOrCircumstances.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cause_or_circumstances is required when disposition_type is DIED");
        }

        OffsetDateTime lastSeenAt = offsetDateTimeVal(body, "lastSeenAt", "last_seen_at");
        if (LAST_SEEN_REQUIRED.contains(dispositionType) && lastSeenAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "last_seen_at is required for disposition_type " + dispositionType);
        }

        EmergencyDispositionEntity disposition = new EmergencyDispositionEntity();
        disposition.setTenantId(tenantId);
        disposition.setEpisodeId(episodeId);
        disposition.setDispositionType(dispositionType);
        disposition.setDispositionReason(reason);
        disposition.setDisposedBy(disposedBy);
        disposition.setDestination(destination);
        disposition.setCauseOrCircumstances(causeOrCircumstances);
        disposition.setLastSeenAt(lastSeenAt);

        try {
            disposition = dispositionRepository.save(disposition);
        } catch (DataIntegrityViolationException dup) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A disposition has already been recorded for this episode");
        }

        EmergencyEpisodeState terminal = TERMINATES_EPISODE_AS.get(dispositionType);
        if (terminal != null) {
            try {
                episodeService.transition(episodeId, tenantId, terminal, dispositionType);
            } catch (IllegalStateException invalidTransition) {
                // Reuses the shared FSM guard (EmergencyEpisodeService.transitionTo), which throws
                // unchecked and is otherwise unmapped in this service — a clear 409 here beats an
                // opaque 500 for a disposition that contradicts the episode's actual state (e.g.
                // LEFT_BEFORE_ASSESSMENT recorded against an episode already OPEN_IN_CARE).
                throw new ResponseStatusException(HttpStatus.CONFLICT, invalidTransition.getMessage());
            }
        }

        return disposition;
    }

    @Transactional(readOnly = true)
    public EmergencyDispositionEntity forEpisode(UUID episodeId, UUID tenantId) {
        return dispositionRepository.findByEpisodeIdAndTenantId(episodeId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No disposition recorded for this episode"));
    }

    private static String str(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    private static OffsetDateTime offsetDateTimeVal(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        try { return OffsetDateTime.parse(s.trim()); } catch (java.time.format.DateTimeParseException e) { return null; }
    }
}
