package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import zw.gov.mohcc.impilo.pct.domain.EmergencyAlertType;
import zw.gov.mohcc.impilo.pct.integration.RitoSafetyClient;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyAlertEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyAlertRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;

/**
 * Raises the six emergency alerts, and ages unanswered ones to OVERDUE (W5).
 *
 * <p>This is the "patients cannot disappear" mechanism. It sweeps every open episode on a timer and
 * reads only the clocks {@code emergency_episode} already carries as declared DERIVED PROJECTIONS —
 * never a peer service over HTTP, because an alert that depends on a live cross-service read goes
 * quiet exactly when a peer degrades, which is when it matters most.
 *
 * <p>System-initiated: it runs with no TrustContext, so it writes an explicit system actor rather
 * than borrowing a user's identity.
 *
 * <p><b>Per-episode isolation is deliberate.</b> Each episode is swept in its own transaction, so one
 * bad row cannot abort the sweep for every other patient in the estate. A safety sweep that stops at
 * the first exception is a safety sweep that silently protects nobody after the first bad record.
 */
@Component
public class EmergencyAlertSweepJob {

    private static final Logger log = LoggerFactory.getLogger(EmergencyAlertSweepJob.class);

    private final EmergencyEpisodeRepository episodeRepository;
    private final EmergencyAlertRepository alertRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final RitoSafetyClient ritoSafetyClient;

    public EmergencyAlertSweepJob(EmergencyEpisodeRepository episodeRepository,
                                  EmergencyAlertRepository alertRepository,
                                  EventOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper,
                                  RitoSafetyClient ritoSafetyClient) {
        this.episodeRepository = episodeRepository;
        this.alertRepository = alertRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.ritoSafetyClient = ritoSafetyClient;
    }

    @Scheduled(fixedDelayString = "${pct.emergency.alert.sweep-interval-ms:60000}",
            initialDelayString = "${pct.emergency.alert.sweep-initial-delay-ms:45000}")
    public void sweep() {
        int raised = raiseDueAlerts(OffsetDateTime.now(), EmergencyAlertRules.Thresholds.defaults());
        int overdue = ageUnansweredToOverdue(OffsetDateTime.now());
        if (raised > 0 || overdue > 0) {
            log.info("Emergency alert sweep raised {} and aged {} to OVERDUE", raised, overdue);
        }
    }

    /** Evaluate every open episode and raise whatever it warrants. Returns the number raised. */
    public int raiseDueAlerts(OffsetDateTime now, EmergencyAlertRules.Thresholds thresholds) {
        List<EmergencyEpisodeEntity> open =
                episodeRepository.findByStateIn(List.of("OPEN_UNTRIAGED", "OPEN_IN_CARE", "OPEN_AWAITING_ACCEPTANCE"));
        int raised = 0;
        for (EmergencyEpisodeEntity episode : open) {
            try {
                raised += sweepOneEpisode(episode, now, thresholds);
            } catch (RuntimeException ex) {
                // Isolated on purpose — see the class comment. Logged loudly, never rethrown.
                log.error("Emergency alert sweep failed for episode {}: {}",
                        episode.getEpisodeId(), ex.toString());
            }
        }
        return raised;
    }

    /**
     * One episode, one transaction. {@code REQUIRES_NEW} so a failure here rolls back only this
     * episode's alerts.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int sweepOneEpisode(EmergencyEpisodeEntity episode,
                               OffsetDateTime now,
                               EmergencyAlertRules.Thresholds thresholds) {
        // The reconciliation input is false here; the reconciler that computes divergence against
        // peers is wired separately and calls raiseAlert directly. Declared rather than silently
        // omitted, so nobody reads CORRELATION_DIVERGENCE's absence as "no divergence found".
        List<EmergencyAlertRules.Candidate> candidates =
                EmergencyAlertRules.evaluate(episode, now, thresholds, false);

        int raised = 0;
        for (EmergencyAlertRules.Candidate c : candidates) {
            if (raiseAlert(episode, c, now)) {
                raised++;
            }
        }
        return raised;
    }

    /**
     * Raise one alert if there is not already an open one of that type for the episode.
     *
     * <p>Checked first, then guarded by the {@code uq_emergency_alert_open} partial unique index —
     * two sweep instances can race, and the database is the only arbiter that actually holds. A
     * losing race is a no-op, not an error: the alert the other instance raised is just as good.
     */
    public boolean raiseAlert(EmergencyEpisodeEntity episode,
                              EmergencyAlertRules.Candidate candidate,
                              OffsetDateTime now) {
        String type = candidate.type().name();
        if (alertRepository.findOpen(episode.getTenantId(), episode.getEpisodeId(), type).isPresent()) {
            return false;
        }

        EmergencyAlertEntity alert = new EmergencyAlertEntity();
        alert.setTenantId(episode.getTenantId());
        alert.setFacilityId(episode.getFacilityId());
        alert.setEpisodeId(episode.getEpisodeId());
        alert.setSubjectCpid(episode.getSubjectCpid());
        alert.setAlertType(type);
        alert.setSeverity(candidate.type().severity());
        alert.setStatus("RAISED");
        alert.setReason(candidate.reason());
        alert.setDetectedValueAt(candidate.detectedValueAt());
        alert.setRaisedAt(now);
        alert.setRaisedBy(EmergencyAlertType.SYSTEM_ACTOR);
        alert.setResponseDueAt(now.plusMinutes(candidate.type().responseWindowMinutes()));

        if (candidate.type() == EmergencyAlertType.ACCEPTANCE_OVERDUE) {
            // "Set when an ACCEPTANCE_OVERDUE escalates" (V203's own column comment) — raising IS the
            // escalation for this type; it does not wait for a responder's ack/respond first. A rito
            // outage never blocks the alert itself (the case ref is just absent).
            String caseId = ritoSafetyClient.createCase(episode.getTenantId(),
                    "SAFETY_CONCERN", "Emergency handover awaiting acceptance too long",
                    candidate.reason(), "CRITICAL", "EMERGENCY_ACCEPTANCE_DELAY",
                    episode.getSubjectCpid(), Map.of("episodeId", episode.getEpisodeId().toString()),
                    "pct-ea-" + episode.getEpisodeId());
            alert.setRitoCaseRef(caseId);
        }

        try {
            alertRepository.saveAndFlush(alert);
        } catch (DataIntegrityViolationException race) {
            // The partial unique index fired: a concurrent sweep raised the same alert first.
            log.debug("Emergency alert {} for episode {} already raised concurrently",
                    type, episode.getEpisodeId());
            return false;
        }

        emit(alert, "EMERGENCY_ALERT_RAISED");
        return true;
    }

    /**
     * Age RAISED/ACKNOWLEDGED alerts past their response window to OVERDUE.
     *
     * <p>OVERDUE is still an OPEN status — it stays on the board and keeps holding the anti-noise
     * slot. Ageing an unanswered alert into silence would be the exact failure the alert exists to
     * prevent.
     */
    @Transactional
    public int ageUnansweredToOverdue(OffsetDateTime now) {
        List<EmergencyAlertEntity> breached = alertRepository.findBreached(now);
        int aged = 0;
        for (EmergencyAlertEntity a : breached) {
            if ("OVERDUE".equals(a.getStatus())) {
                continue;
            }
            a.setStatus("OVERDUE");
            alertRepository.save(a);
            emit(a, "EMERGENCY_ALERT_OVERDUE");
            aged++;
        }
        return aged;
    }

    private void emit(EmergencyAlertEntity a, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alertId", a.getAlertId() != null ? a.getAlertId().toString() : null);
        payload.put("episodeId", a.getEpisodeId().toString());
        payload.put("tenantId", a.getTenantId().toString());
        payload.put("facilityId", a.getFacilityId() != null ? a.getFacilityId().toString() : null);
        payload.put("alertType", a.getAlertType());
        payload.put("severity", a.getSeverity());
        payload.put("status", a.getStatus());
        payload.put("reason", a.getReason());
        payload.put("responseDueAt", a.getResponseDueAt() != null ? a.getResponseDueAt().toString() : null);
        payload.put("subjectCpid", a.getSubjectCpid());
        payload.put("ritoCaseRef", a.getRitoCaseRef());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("EMERGENCY_ALERT");
        outbox.setAggregateId(a.getEpisodeId().toString());
        outbox.setEventType(eventType);
        outbox.setTenantId(a.getTenantId());
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            log.error("Emergency alert payload could not be serialised: {}", ex.getMessage());
            outbox.setPayload("{\"episodeId\":\"" + a.getEpisodeId() + "\"}");
        }
        outboxRepository.save(outbox);
    }
}
