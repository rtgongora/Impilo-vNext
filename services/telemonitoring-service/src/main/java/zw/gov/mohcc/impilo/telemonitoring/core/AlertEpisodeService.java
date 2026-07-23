package zw.gov.mohcc.impilo.telemonitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.telemonitoring.core.AlertRuleConfigService.EffectiveAlertConfig;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertSeverity;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertTriggerClass;
import zw.gov.mohcc.impilo.telemonitoring.events.TelemonitoringEventEmitter;
import zw.gov.mohcc.impilo.telemonitoring.integration.DaidzaiIncidentClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.PctTaskClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.TeleconsultReferralClient;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.AlertEpisodeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.AlertEpisodeRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringPlanRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AlertEpisode aggregate service (OF-B26): open/attach with the one-live-episode race
 * guard, the §14.7 escalation-ladder actions, acknowledgement, and §14.6 accountable
 * closure. All external dispatches (PCT tasks, the Vol I teleconsult front door,
 * Daidzai EMS) are best-effort and never block or roll back the alert record — the
 * outbox events are the durable seam and every dispatch outcome is recorded honestly
 * on the episode and in its ladder history.
 */
@Service
public class AlertEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(AlertEpisodeService.class);

    /** §14.7 rung actors/actions implemented with real seams; others are recorded-only. */
    public static final int RUNG_CHW_TASK = 4;
    public static final int RUNG_SCHEDULED_REVIEW = 6;
    public static final int RUNG_URGENT_TELECONSULT = 7;
    public static final int RUNG_DAIDZAI = 11;
    public static final int RUNG_MAX = 15;

    private final AlertEpisodeRepository episodeRepository;
    private final MonitoringPlanRepository planRepository;
    private final TelemonitoringEventEmitter eventEmitter;
    private final PctTaskClient pctTaskClient;
    private final TeleconsultReferralClient teleconsultReferralClient;
    private final DaidzaiIncidentClient daidzaiIncidentClient;
    private final ObjectMapper objectMapper;

    public AlertEpisodeService(AlertEpisodeRepository episodeRepository,
                               MonitoringPlanRepository planRepository,
                               TelemonitoringEventEmitter eventEmitter,
                               PctTaskClient pctTaskClient,
                               TeleconsultReferralClient teleconsultReferralClient,
                               DaidzaiIncidentClient daidzaiIncidentClient,
                               ObjectMapper objectMapper) {
        this.episodeRepository = episodeRepository;
        this.planRepository = planRepository;
        this.eventEmitter = eventEmitter;
        this.pctTaskClient = pctTaskClient;
        this.teleconsultReferralClient = teleconsultReferralClient;
        this.daidzaiIncidentClient = daidzaiIncidentClient;
        this.objectMapper = objectMapper;
    }

    // ── Open-or-attach (evaluation entry point) ─────────────────────────────────

    /** One contributing reading, with its raw (uncapped) band severity and trust gate. */
    public record ContributingReading(
            UUID readingId,
            String metricCode,
            String value,
            String readingStatus,
            String trustLevel,
            OffsetDateTime measuredAt,
            AlertSeverity rawSeverity,
            boolean gated) {
    }

    public record OpenOrAttachCommand(
            UUID tenantId,
            UUID planId,
            String patientCpid,
            String deviceRef,
            AlertTriggerClass triggerClass,
            String liveGuard,
            AlertSeverity openSeverity,       // already ceiling-capped for a lone reading
            boolean openCeilingApplied,
            ContributingReading contribution, // null for DEVICE_OFFLINE (no reading)
            EffectiveAlertConfig config,
            boolean quietAttach) {            // device-quality storms attach without events
    }

    /**
     * One clinical situation = one episode (§14.6): opens a new episode under the
     * {@code live_guard} UNIQUE constraint, or attaches to the live one. A lost
     * open-race falls through to attach — sibling episodes cannot exist.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AlertEpisodeEntity openOrAttach(OpenOrAttachCommand cmd) {
        AlertEpisodeEntity existing = episodeRepository.findByLiveGuard(cmd.liveGuard()).orElse(null);
        if (existing != null) {
            return attach(existing, cmd);
        }
        AlertEpisodeEntity episode = new AlertEpisodeEntity();
        episode.setTenantId(cmd.tenantId());
        episode.setPlanId(cmd.planId());
        episode.setPatientCpid(cmd.patientCpid());
        episode.setDeviceRef(cmd.deviceRef());
        episode.setTriggerClass(cmd.triggerClass());
        episode.setMetricCode(cmd.contribution() != null ? cmd.contribution().metricCode() : null);
        episode.setInitialSeverity(cmd.openSeverity());
        episode.setSeverity(cmd.openSeverity());
        episode.setStatus(AlertStatus.OPEN);
        episode.setSeverityCeilingApplied(cmd.openCeilingApplied());
        episode.setLiveGuard(cmd.liveGuard());
        episode.setBreachCount(cmd.contribution() != null ? 1 : 0);
        OffsetDateTime now = OffsetDateTime.now();
        if (cmd.contribution() != null) {
            episode.setLastBreachAt(cmd.contribution().measuredAt());
            episode.setContributingReadings(appendJson("[]", contributionNode(cmd.contribution())));
        }
        episode.setResponseDeadlineAt(now.plusMinutes(cmd.config().responseMinutesFor(cmd.openSeverity())));
        try {
            episode = episodeRepository.saveAndFlush(episode);
        } catch (DataIntegrityViolationException e) {
            // Lost the open race — the UNIQUE(live_guard) held; attach to the winner.
            AlertEpisodeEntity winner = episodeRepository.findByLiveGuard(cmd.liveGuard())
                    .orElseThrow(() -> e);
            return attach(winner, cmd);
        }
        emitAlert("opened", episode, basePayload(episode));
        if (episode.getSeverity() == AlertSeverity.EMERGENCY) {
            autoEmergencyDispatch(episode);
        }
        return episode;
    }

    /**
     * Attach a breach/signal to the live episode: repeat breaches never spawn siblings
     * (§14.6 dedup/storm rule). Repeat-abnormal (trigger 5) and the §14.6 low-trust
     * ceiling are applied here: severity may only rise past AMBER when at least one
     * NON-gated (trusted, fully-valid) reading contributes.
     */
    private AlertEpisodeEntity attach(AlertEpisodeEntity episode, OpenOrAttachCommand cmd) {
        OffsetDateTime now = OffsetDateTime.now();
        ContributingReading contribution = cmd.contribution();
        if (contribution != null) {
            episode.setContributingReadings(
                    appendJson(episode.getContributingReadings(), contributionNode(contribution)));
            episode.setBreachCount(episode.getBreachCount() + 1);
            episode.setLastBreachAt(contribution.measuredAt());
        }
        if (cmd.quietAttach() || contribution == null || !cmd.triggerClass().isClinical()) {
            return episodeRepository.save(episode); // storm control: no event multiplication
        }

        AlertSeverity target = max(episode.getSeverity(), contribution.rawSeverity());
        boolean repeatAbnormal = recentBreachCount(episode, now, cmd.config().repeatAbnormalWindowMinutes())
                >= cmd.config().repeatAbnormalCount();
        String escalationReason = "THRESHOLD_BREACH";
        if (repeatAbnormal) {
            target = target.escalated();
            episode.setTriggerClass(AlertTriggerClass.REPEAT_ABNORMAL);
            escalationReason = "REPEAT_ABNORMAL";
        }
        boolean trustedEvidence = !contribution.gated() || hasTrustedContribution(episode);
        if (!trustedEvidence && target.isAtLeast(AlertSeverity.RED)) {
            // §14.6 ceiling: degraded/low-trust evidence alone may not exceed AMBER.
            target = AlertSeverity.AMBER;
            episode.setSeverityCeilingApplied(true);
        } else if (trustedEvidence) {
            episode.setSeverityCeilingApplied(false);
        }

        if (target != episode.getSeverity() && target.isAtLeast(episode.getSeverity())) {
            AlertSeverity previous = episode.getSeverity();
            episode.setSeverity(target);
            episode.setResponseDeadlineAt(now.plusMinutes(cmd.config().responseMinutesFor(target)));
            episode = episodeRepository.save(episode);
            Map<String, Object> payload = basePayload(episode);
            payload.put("previousSeverity", previous.name());
            payload.put("escalationReason", escalationReason);
            emitAlert("escalated", episode, payload);
            if (target == AlertSeverity.EMERGENCY) {
                autoEmergencyDispatch(episode);
            }
            return episode;
        }
        return episodeRepository.save(episode);
    }

    // ── §14.7 ladder actions ────────────────────────────────────────────────────

    @Transactional
    public AlertEpisodeEntity acknowledge(UUID episodeId, String actor) {
        AlertEpisodeEntity episode = load(episodeId);
        requireText(actor, "An identified accountable actor is required to acknowledge an alert (§14.6)");
        transition(episode, AlertStatus.ACKNOWLEDGED);
        episode.setAcknowledgedBy(actor);
        episode.setAcknowledgedAt(OffsetDateTime.now());
        episode = episodeRepository.save(episode);
        Map<String, Object> payload = basePayload(episode);
        payload.put("acknowledgedLate", episode.getResponseDeadlineAt() != null
                && episode.getAcknowledgedAt().isAfter(episode.getResponseDeadlineAt()));
        emitAlert("acknowledged", episode, payload);
        return episode;
    }

    /** ACKNOWLEDGED/ESCALATED → IN_PROGRESS (a human is actively working the episode). */
    @Transactional
    public AlertEpisodeEntity startProgress(UUID episodeId, String actor) {
        AlertEpisodeEntity episode = load(episodeId);
        requireText(actor, "An identified actor is required");
        transition(episode, AlertStatus.IN_PROGRESS);
        episode.setLadderHistory(appendJson(episode.getLadderHistory(),
                ladderNode(null, actor, "IN_PROGRESS", "WORKING")));
        return episodeRepository.save(episode);
    }

    /**
     * Record a §14.7 ladder rung. The status machine's only path to ESCALATED runs
     * through here, so ESCALATED always has a recorded rung. Rung side effects
     * (all best-effort, outcome recorded honestly):
     * rung 4 — PCT CHW task; rung 6/7 — Vol I teleconsult case with
     * origin=MONITORING_ALERT provenance; rung 11 — Daidzai EMS incident.
     * Rungs 12/13 (Nhume/Ndila) and 14/15 are recorded-only — their transactional
     * seams are OF-B26 deferrals reported in the wave notes.
     */
    @Transactional
    public AlertEpisodeEntity recordLadderStep(UUID episodeId, int rung, String actor, String note) {
        AlertEpisodeEntity episode = load(episodeId);
        requireText(actor, "An identified actor is required to record a ladder step (§14.7)");
        if (rung < 1 || rung > RUNG_MAX) {
            throw new TelemonitoringDomainException("TM_ALERT_INVALID_RUNG", 400,
                    "Ladder rung must be 1.." + RUNG_MAX + " (§14.7), got " + rung);
        }
        if (!episode.getStatus().isLive()) {
            throw new TelemonitoringDomainException("TM_ALERT_ALREADY_RESOLVED", 409,
                    "Episode " + episodeId + " is RESOLVED — the ladder is closed");
        }
        String action;
        String outcome;
        switch (rung) {
            case RUNG_CHW_TASK -> {
                boolean dispatched = dispatchChwTask(episode, actor);
                episode.setChwTaskDispatched(dispatched);
                action = "CHW_PCT_TASK";
                outcome = dispatched ? "TASK_DISPATCHED" : "TASK_DISPATCH_FAILED_EVENT_DURABLE";
            }
            case RUNG_SCHEDULED_REVIEW, RUNG_URGENT_TELECONSULT -> {
                String referralRef = episode.getReviewReferralRef() != null
                        ? episode.getReviewReferralRef()
                        : teleconsultReferralClient.createMonitoringReviewReferral(
                                episode.getTenantId(), episode.getId(), episode.getPatientCpid(),
                                episode.getTriggerClass().name(), episode.getMetricCode(),
                                rung == RUNG_URGENT_TELECONSULT);
                episode.setReviewReferralRef(referralRef);
                action = rung == RUNG_URGENT_TELECONSULT ? "URGENT_TELECONSULT" : "SCHEDULED_VIRTUAL_REVIEW";
                outcome = referralRef != null ? "CASE_CREATED:" + referralRef : "CASE_CREATE_FAILED_EVENT_DURABLE";
            }
            case RUNG_DAIDZAI -> {
                dispatchDaidzai(episode);
                action = "DAIDZAI_EMS_DISPATCH";
                outcome = Boolean.TRUE.equals(episode.getEmsDispatched())
                        ? "INCIDENT_CREATED:" + episode.getEmsIncidentRef()
                        : "INCIDENT_CREATE_FAILED_EVENT_DURABLE";
            }
            default -> {
                action = "LADDER_STEP";
                outcome = "RECORDED";
            }
        }
        episode.setLadderHistory(appendJson(episode.getLadderHistory(), ladderNode(rung, actor, action, outcome)));
        episode.setCurrentRung(rung);
        if (episode.getStatus() != AlertStatus.ESCALATED) {
            transition(episode, AlertStatus.ESCALATED);
        }
        episode = episodeRepository.save(episode);

        Map<String, Object> payload = basePayload(episode);
        payload.put("rung", rung);
        payload.put("rungAction", action);
        payload.put("rungOutcome", outcome);
        emitAlert("escalated", episode, payload);
        if (note != null && !note.isBlank()) {
            log.info("Ladder note recorded off-event for episode {} (wording law: notes never ride events)",
                    episode.getId());
        }
        return episode;
    }

    /** Journey #61 rung-15 law: physical care must record assumption of responsibility. */
    @Transactional
    public AlertEpisodeEntity recordAssumptionOfCare(UUID episodeId, String note, String actor) {
        AlertEpisodeEntity episode = load(episodeId);
        requireText(actor, "An identified actor is required to record assumption of care");
        requireText(note, "An assumption-of-care note is required (journey #61)");
        if (!episode.getStatus().isLive()) {
            throw new TelemonitoringDomainException("TM_ALERT_ALREADY_RESOLVED", 409,
                    "Episode " + episodeId + " is already RESOLVED");
        }
        episode.setAssumptionOfCareNote(note);
        episode.setAssumptionOfCareBy(actor);
        episode.setAssumptionOfCareAt(OffsetDateTime.now());
        episode.setLadderHistory(appendJson(episode.getLadderHistory(),
                ladderNode(RUNG_MAX, actor, "ASSUMPTION_OF_CARE", "CARE_ASSUMED")));
        return episodeRepository.save(episode);
    }

    /**
     * §14.6 accountable closure — the invariant: an episode closes ONLY with reviewer
     * identity, clinical assessment, action taken and outcome classification. Closure
     * without any of the four is REFUSED. EMERGENCY episodes additionally refuse
     * closure until assumption of care is recorded (journey #61). Bulk-close does not
     * exist; there is no other path out of the live states.
     */
    @Transactional
    public AlertEpisodeEntity resolve(UUID episodeId, String resolvedBy, String assessment,
                                      String actionTaken, String outcome) {
        AlertEpisodeEntity episode = load(episodeId);
        if (isBlank(resolvedBy) || isBlank(assessment) || isBlank(actionTaken) || isBlank(outcome)) {
            throw new TelemonitoringDomainException("TM_ALERT_CLOSURE_INCOMPLETE", 422,
                    "Accountable closure requires ALL of: reviewer identity, clinical assessment, "
                            + "action taken, outcome classification (§14.6). Closure refused.");
        }
        if (episode.getSeverity() == AlertSeverity.EMERGENCY && isBlank(episode.getAssumptionOfCareNote())) {
            throw new TelemonitoringDomainException("TM_ALERT_ASSUMPTION_OF_CARE_REQUIRED", 422,
                    "EMERGENCY episodes cannot resolve until assumption of care is recorded "
                            + "(journey #61 / §14.7 rung 15). Unresolved handover is a named failure mode.");
        }
        transition(episode, AlertStatus.RESOLVED);
        OffsetDateTime now = OffsetDateTime.now();
        episode.setResolvedBy(resolvedBy);
        episode.setAssessment(assessment);
        episode.setActionTaken(actionTaken);
        episode.setOutcome(outcome);
        episode.setResolvedAt(now);
        episode.setLiveGuard(null); // release the one-live-episode guard
        episode = episodeRepository.save(episode);
        Map<String, Object> payload = basePayload(episode);
        payload.put("outcome", outcome); // coded classification — assessment text never rides events
        payload.put("resolvedBy", resolvedBy);
        emitAlert("resolved", episode, payload);
        return episode;
    }

    // ── Reads ──

    @Transactional(readOnly = true)
    public AlertEpisodeEntity get(UUID episodeId) {
        return load(episodeId);
    }

    @Transactional(readOnly = true)
    public List<AlertEpisodeEntity> list(UUID tenantId, UUID planId, AlertStatus status, String deviceRef) {
        if (planId != null) {
            return episodeRepository.findByTenantIdAndPlanIdOrderByCreatedAtDesc(tenantId, planId);
        }
        if (deviceRef != null) {
            return episodeRepository.findByTenantIdAndDeviceRefOrderByCreatedAtDesc(tenantId, deviceRef);
        }
        if (status != null) {
            return episodeRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        }
        return episodeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    /** Overdue-sweep population: live unacknowledged episodes past their deadline. */
    @Transactional(readOnly = true)
    public List<AlertEpisodeEntity> listOverdue(OffsetDateTime now) {
        return episodeRepository.findByStatusAndResponseDeadlineAtBefore(AlertStatus.OPEN, now);
    }

    // ── Overdue auto-escalation (§14.6 escalation-on-silence) ───────────────────

    /**
     * Escalate an unacknowledged episode past its response window: severity rises one
     * level (deadline resets), the silence is audited in ladder history, and
     * {@code telemonitoring.alert.escalated.v1} fires. The §14.6 low-trust ceiling
     * holds — clock silence is not clinical evidence, so ceilinged episodes are
     * audited and re-notified but never pushed past AMBER by time alone. Reaching
     * EMERGENCY triggers the rung-11 Daidzai dispatch.
     */
    @Transactional
    public boolean escalateOverdue(UUID episodeId, EffectiveAlertConfig config) {
        AlertEpisodeEntity episode = load(episodeId);
        if (episode.getStatus() != AlertStatus.OPEN) {
            return false; // acknowledged/resolved while the sweep ran
        }
        OffsetDateTime now = OffsetDateTime.now();
        boolean ceilingHolds = episode.isSeverityCeilingApplied();
        AlertSeverity previous = episode.getSeverity();
        AlertSeverity target = ceilingHolds ? previous : previous.escalated();
        if (target == previous && episode.getAutoEscalatedAt() != null) {
            return false; // saturated (EMERGENCY or ceilinged) and already audited once
        }
        episode.setAutoEscalatedAt(now);
        episode.setSeverity(target);
        episode.setResponseDeadlineAt(now.plusMinutes(config.responseMinutesFor(target)));
        episode.setLadderHistory(appendJson(episode.getLadderHistory(),
                ladderNode(episode.getCurrentRung(), "system", "AUTO_ESCALATE_OVERDUE",
                        target != previous ? "SEVERITY_" + target : "OVERDUE_AUDITED")));
        episode = episodeRepository.save(episode);
        Map<String, Object> payload = basePayload(episode);
        payload.put("previousSeverity", previous.name());
        payload.put("escalationReason", "OVERDUE_UNACKNOWLEDGED");
        emitAlert("escalated", episode, payload);
        if (target == AlertSeverity.EMERGENCY && previous != AlertSeverity.EMERGENCY) {
            autoEmergencyDispatch(episode);
        }
        return true;
    }

    // ── Rung-4 helper reused by the offline sweep (journey #65) ────────────────

    /**
     * Auto rung-4: PCT task to the plan's assigned CHW, ladder-recorded with an honest
     * dispatch outcome. Used by the device-offline sweep and the rung-4 action.
     */
    @Transactional
    public AlertEpisodeEntity autoChwTask(UUID episodeId) {
        AlertEpisodeEntity episode = load(episodeId);
        boolean dispatched = dispatchChwTask(episode, "system");
        episode.setChwTaskDispatched(dispatched);
        episode.setCurrentRung(RUNG_CHW_TASK);
        episode.setLadderHistory(appendJson(episode.getLadderHistory(),
                ladderNode(RUNG_CHW_TASK, "system", "CHW_PCT_TASK",
                        dispatched ? "TASK_DISPATCHED" : "TASK_DISPATCH_FAILED_EVENT_DURABLE")));
        episode = episodeRepository.save(episode);
        Map<String, Object> payload = basePayload(episode);
        payload.put("rung", RUNG_CHW_TASK);
        payload.put("chwTaskDispatched", dispatched);
        emitAlert("task_requested", episode, payload);
        return episode;
    }

    // ── Internals ──

    /**
     * §14.7: an EMERGENCY-severity episode enters the ladder at rung 11+ — a Daidzai
     * EMS incident is raised (best-effort), recorded, and the episode moves to
     * ESCALATED with the rung on record.
     */
    private void autoEmergencyDispatch(AlertEpisodeEntity episode) {
        if (episode.getEmsIncidentRef() != null) {
            return; // one EMS incident per episode
        }
        dispatchDaidzai(episode);
        episode.setCurrentRung(RUNG_DAIDZAI);
        episode.setLadderHistory(appendJson(episode.getLadderHistory(),
                ladderNode(RUNG_DAIDZAI, "system", "DAIDZAI_EMS_DISPATCH",
                        Boolean.TRUE.equals(episode.getEmsDispatched())
                                ? "INCIDENT_CREATED:" + episode.getEmsIncidentRef()
                                : "INCIDENT_CREATE_FAILED_EVENT_DURABLE")));
        if (AlertStatus.canTransition(episode.getStatus(), AlertStatus.ESCALATED)) {
            episode.setStatus(AlertStatus.ESCALATED);
        }
        episodeRepository.save(episode);
        Map<String, Object> payload = basePayload(episode);
        payload.put("rung", RUNG_DAIDZAI);
        payload.put("escalationReason", "EMERGENCY_SEVERITY");
        payload.put("emsDispatched", episode.getEmsDispatched());
        if (episode.getEmsIncidentRef() != null) {
            payload.put("emsIncidentRef", episode.getEmsIncidentRef());
        }
        emitAlert("escalated", episode, payload);
    }

    private void dispatchDaidzai(AlertEpisodeEntity episode) {
        String incidentRef = daidzaiIncidentClient.createIncident(
                episode.getTenantId(), episode.getId(), episode.getPatientCpid(),
                episode.getTriggerClass().name(), episode.getMetricCode());
        episode.setEmsIncidentRef(incidentRef);
        episode.setEmsDispatched(incidentRef != null);
    }

    private boolean dispatchChwTask(AlertEpisodeEntity episode, String requestedBy) {
        String chwId = chwIdFor(episode);
        if (chwId == null) {
            log.warn("Episode {} rung 4: plan carries no CHW binding — task recorded undispatched",
                    episode.getId());
            return false;
        }
        // Coded notes only (§14.6 wording law) — trigger + metric + episode ref, no clinical text.
        String notes = "Monitoring alert follow-up — trigger " + episode.getTriggerClass()
                + (episode.getMetricCode() != null ? ", metric " + episode.getMetricCode() : "")
                + ", episode " + episode.getId();
        return pctTaskClient.createTask(episode.getTenantId(), "MONITORING_ALERT_FOLLOWUP",
                chwId, "CHW", null, null, notes);
    }

    private String chwIdFor(AlertEpisodeEntity episode) {
        if (episode.getPlanId() == null) {
            return null;
        }
        MonitoringPlanEntity plan = planRepository.findById(episode.getPlanId()).orElse(null);
        if (plan == null || plan.getCareTeam() == null || plan.getCareTeam().isBlank()) {
            return null;
        }
        try {
            JsonNode careTeam = objectMapper.readTree(plan.getCareTeam());
            if (careTeam.hasNonNull("chwId")) return careTeam.get("chwId").asText();
            if (careTeam.hasNonNull("chw_id")) return careTeam.get("chw_id").asText();
            JsonNode chw = careTeam.get("chw");
            if (chw != null && chw.isObject()) {
                if (chw.hasNonNull("id")) return chw.get("id").asText();
                if (chw.hasNonNull("chwId")) return chw.get("chwId").asText();
            }
        } catch (Exception e) {
            log.warn("Episode {}: plan care_team unparseable — CHW task undispatched: {}",
                    episode.getId(), e.getMessage());
        }
        return null;
    }

    private int recentBreachCount(AlertEpisodeEntity episode, OffsetDateTime now, int windowMinutes) {
        OffsetDateTime cutoff = now.minusMinutes(windowMinutes);
        int count = 0;
        for (JsonNode entry : readArray(episode.getContributingReadings())) {
            JsonNode at = entry.get("measuredAt");
            if (at == null || at.isNull()) continue;
            try {
                if (!OffsetDateTime.parse(at.asText()).isBefore(cutoff)) {
                    count++;
                }
            } catch (Exception ignored) {
                // unparseable timestamps don't count toward escalation
            }
        }
        return count;
    }

    private boolean hasTrustedContribution(AlertEpisodeEntity episode) {
        for (JsonNode entry : readArray(episode.getContributingReadings())) {
            JsonNode gated = entry.get("gated");
            if (gated != null && !gated.asBoolean(true)) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode contributionNode(ContributingReading c) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("readingId", c.readingId() != null ? c.readingId().toString() : null);
        node.put("metricCode", c.metricCode());
        node.put("value", c.value());
        node.put("readingStatus", c.readingStatus());
        node.put("trustLevel", c.trustLevel());
        node.put("measuredAt", c.measuredAt() != null ? c.measuredAt().toString() : null);
        node.put("rawSeverity", c.rawSeverity() != null ? c.rawSeverity().name() : null);
        node.put("gated", c.gated());
        return node;
    }

    private ObjectNode ladderNode(Integer rung, String actor, String action, String outcome) {
        ObjectNode node = objectMapper.createObjectNode();
        if (rung != null) {
            node.put("rung", rung);
        }
        node.put("at", OffsetDateTime.now().toString());
        node.put("actor", actor);
        node.put("action", action);
        node.put("outcome", outcome);
        return node;
    }

    private ArrayNode readArray(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "[]" : json);
            return node.isArray() ? (ArrayNode) node : objectMapper.createArrayNode();
        } catch (Exception e) {
            return objectMapper.createArrayNode();
        }
    }

    private String appendJson(String arrayJson, ObjectNode entry) {
        ArrayNode array = readArray(arrayJson);
        array.add(entry);
        return array.toString();
    }

    /** §14.6 wording law: coded identifiers only — no free text, no values, no diagnosis. */
    private Map<String, Object> basePayload(AlertEpisodeEntity episode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("episodeId", episode.getId().toString());
        payload.put("planId", episode.getPlanId() != null ? episode.getPlanId().toString() : null);
        payload.put("deviceRef", episode.getDeviceRef());
        payload.put("triggerClass", episode.getTriggerClass().name());
        payload.put("metricCode", episode.getMetricCode());
        payload.put("severity", episode.getSeverity().name());
        payload.put("status", episode.getStatus().name());
        payload.put("breachCount", episode.getBreachCount());
        payload.put("severityCeilingApplied", episode.isSeverityCeilingApplied());
        payload.put("currentRung", episode.getCurrentRung());
        return payload;
    }

    private void emitAlert(String action, AlertEpisodeEntity episode, Map<String, Object> payload) {
        eventEmitter.emitAlertEvent(action, episode.getId(), episode.getPatientCpid(),
                episode.getDeviceRef(), payload, episode.getTenantId());
    }

    private void transition(AlertEpisodeEntity episode, AlertStatus to) {
        if (!AlertStatus.canTransition(episode.getStatus(), to)) {
            throw new TelemonitoringDomainException("TM_ALERT_INVALID_TRANSITION", 409,
                    "Alert episode transition " + episode.getStatus() + " -> " + to
                            + " is not permitted (§14.6 guarded machine)");
        }
        episode.setStatus(to);
    }

    private AlertEpisodeEntity load(UUID episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> new TelemonitoringDomainException("TM_ALERT_NOT_FOUND", 404,
                        "Alert episode not found: " + episodeId));
    }

    private static void requireText(String value, String message) {
        if (isBlank(value)) {
            throw new TelemonitoringDomainException("TM_ALERT_VALIDATION", 400, message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static AlertSeverity max(AlertSeverity a, AlertSeverity b) {
        return a.isAtLeast(b) ? a : b;
    }
}
