package zw.gov.mohcc.impilo.pct.core;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyObservationStayEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyObservationStayRepository;

/**
 * Observation / short-stay (pct V208, W9b) — genuinely absent before this table. Not an admission
 * (no ward bed, no admission record) and not a discharge. Deliberately not FK-coupled to
 * {@link EmergencyDispositionService} in either direction: a clinician can start a stay before
 * formally recording the OBSERVATION disposition that triggered it, and the two correlate only by
 * episode_id.
 *
 * <p>Does not touch the episode's FSM state — the patient stays OPEN_IN_CARE for the whole
 * observation window. Only one open stay per episode ({@code uq_emergency_observation_stay_open});
 * the database partial unique index is the real guarantee, mirrored here for a clear 409.
 */
@Service
public class EmergencyObservationStayService {

    private static final Set<String> VALID_OUTCOMES =
            Set.of("DISCHARGED", "ADMITTED", "ESCALATED_TO_RESUS", "ABSCONDED");

    private final EmergencyEpisodeRepository episodeRepository;
    private final EmergencyObservationStayRepository stayRepository;

    public EmergencyObservationStayService(EmergencyEpisodeRepository episodeRepository,
                                           EmergencyObservationStayRepository stayRepository) {
        this.episodeRepository = episodeRepository;
        this.stayRepository = stayRepository;
    }

    @Transactional
    public EmergencyObservationStayEntity start(UUID episodeId, UUID tenantId, Map<String, Object> body) {
        episodeRepository.findByEpisodeIdAndTenantId(episodeId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergency episode not found"));

        String startedBy = str(body, "startedBy", "started_by");
        if (startedBy == null || startedBy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "started_by is required");
        }

        stayRepository.findOpen(tenantId, episodeId).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Episode " + episodeId + " already has an open observation stay (" + existing.getStayId() + ")");
        });

        EmergencyObservationStayEntity stay = new EmergencyObservationStayEntity();
        stay.setTenantId(tenantId);
        stay.setEpisodeId(episodeId);
        stay.setStartedBy(startedBy);
        stay.setMinObservationMinutes(intVal(body, "minObservationMinutes", "min_observation_minutes"));
        stay.setNotes(str(body, "notes"));

        try {
            return stayRepository.save(stay);
        } catch (DataIntegrityViolationException race) {
            // The partial unique index fired: a concurrent request opened one first.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Episode " + episodeId + " already has an open observation stay");
        }
    }

    @Transactional
    public EmergencyObservationStayEntity end(UUID stayId, UUID tenantId, Map<String, Object> body) {
        EmergencyObservationStayEntity stay = stayRepository.findByStayIdAndTenantId(stayId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Observation stay not found"));
        if (stay.getEndedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Observation stay " + stayId + " has already ended");
        }

        String endedBy = str(body, "endedBy", "ended_by");
        String outcome = str(body, "outcome");
        if (endedBy == null || endedBy.isBlank() || outcome == null || !VALID_OUTCOMES.contains(outcome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ended_by is required and outcome must be one of " + VALID_OUTCOMES);
        }

        stay.setEndedAt(OffsetDateTime.now());
        stay.setEndedBy(endedBy);
        stay.setOutcome(outcome);
        String closingNotes = str(body, "notes");
        if (closingNotes != null) {
            stay.setNotes(closingNotes);
        }
        return stayRepository.save(stay);
    }

    @Transactional(readOnly = true)
    public List<EmergencyObservationStayEntity> forEpisode(UUID episodeId, UUID tenantId) {
        return stayRepository.findByTenantIdAndEpisodeIdOrderByStartedAtDesc(tenantId, episodeId);
    }

    private static String str(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    private static Integer intVal(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
