package zw.gov.mohcc.impilo.telemonitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.telemonitoring.core.AlertEpisodeService.ContributingReading;
import zw.gov.mohcc.impilo.telemonitoring.core.AlertEpisodeService.OpenOrAttachCommand;
import zw.gov.mohcc.impilo.telemonitoring.core.AlertRuleConfigService.EffectiveAlertConfig;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertSeverity;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertTriggerClass;
import zw.gov.mohcc.impilo.telemonitoring.domain.DeviceAssignmentStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.ReadingStatus;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.AlertEpisodeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.DeviceAssignmentEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.TelemetryReadingEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.ThresholdProfileEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.DeviceAssignmentRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringPlanRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.TelemetryReadingRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.ThresholdProfileRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * OF-B26 rule evaluation (§14.5 steps 15/16, §14.6). Fired through the
 * {@link ReadingCompletionListener} seam after each reading completes ingestion, plus
 * two scheduled sweeps (device-offline heartbeat, overdue acknowledgement).
 *
 * <p><b>Rule source.</b> Threshold bands come from the plan's CURRENT immutable
 * threshold-profile version ({@code tm_threshold_profiles.parameters}) — personalised,
 * approval-gated, never duplicated into a parallel rule store (V005 design decision).
 * Band vocabulary per metric: {@code greenMax/amberMax/redMax} (upper direction) and
 * {@code greenMin/amberMin/redMin} (lower direction):
 * beyond green = AMBER, beyond amber = RED, beyond red = EMERGENCY.</p>
 *
 * <p><b>§14.6 low-trust ceiling.</b> DEGRADED_VALID readings and readings whose trust
 * grade is explicitly consumer/low may alone raise at most AMBER (prompting a verified
 * repeat), never RED/EMERGENCY — the cap is stamped on the episode
 * ({@code severity_ceiling_applied}). UNGRADED readings from verified clinical devices
 * pass ungated: grading absence is OF-B25's iot-ingestion half (OD-14), and gating on
 * it would neuter every RED/EMERGENCY alert in the estate today. QUARANTINED readings
 * never value-alert at all — they feed only the DEVICE_QUALITY trigger.</p>
 */
@Service
public class AlertEvaluationService implements ReadingCompletionListener {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationService.class);

    /** Explicitly low-trust grades (§14.1 caveat / OF-G17) — gated to the AMBER ceiling. */
    static final Set<String> LOW_TRUST_GRADES = Set.of("CONSUMER", "LOW", "UNTRUSTED", "WEARABLE");

    static final String CLINICAL_GUARD_SUFFIX = ":CLINICAL";

    private final TelemetryReadingRepository readingRepository;
    private final ThresholdProfileRepository thresholdRepository;
    private final MonitoringPlanRepository planRepository;
    private final DeviceAssignmentRepository assignmentRepository;
    private final AlertEpisodeService episodeService;
    private final AlertRuleConfigService configService;
    private final ObjectMapper objectMapper;

    public AlertEvaluationService(TelemetryReadingRepository readingRepository,
                                  ThresholdProfileRepository thresholdRepository,
                                  MonitoringPlanRepository planRepository,
                                  DeviceAssignmentRepository assignmentRepository,
                                  AlertEpisodeService episodeService,
                                  AlertRuleConfigService configService,
                                  ObjectMapper objectMapper) {
        this.readingRepository = readingRepository;
        this.thresholdRepository = thresholdRepository;
        this.planRepository = planRepository;
        this.assignmentRepository = assignmentRepository;
        this.episodeService = episodeService;
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    // ── The ingestion seam ──────────────────────────────────────────────────────

    @Override
    public void onReadingCompleted(TelemetryReadingEntity reading) {
        if (reading == null) {
            return;
        }
        if (reading.getStatus() == ReadingStatus.QUARANTINED
                || reading.getStatus() == ReadingStatus.DEGRADED_VALID) {
            evaluateDeviceQuality(reading);
        }
        if (reading.getStatus() != ReadingStatus.QUARANTINED && reading.getPlanId() != null) {
            evaluateThreshold(reading);
        }
    }

    // ── Trigger 1/2/5: threshold breach against the plan's current profile ─────

    void evaluateThreshold(TelemetryReadingEntity reading) {
        if (reading.getMetricValue() == null) {
            return;
        }
        ThresholdProfileEntity profile = thresholdRepository
                .findFirstByPlanIdOrderByVersionDesc(reading.getPlanId()).orElse(null);
        if (profile == null) {
            return; // no profile — nothing personalised to evaluate against
        }
        JsonNode band = bandFor(profile.getParameters(), reading.getMetricCode());
        if (band == null) {
            return; // metric not governed by this plan's profile
        }
        AlertSeverity raw = classify(reading.getMetricValue(), band);
        if (raw == null) {
            return; // in the green band — no alert
        }
        boolean gated = isGated(reading);
        AlertSeverity capped = gated && raw.isAtLeast(AlertSeverity.RED) ? AlertSeverity.AMBER : raw;

        MonitoringPlanEntity plan = planRepository.findById(reading.getPlanId()).orElse(null);
        EffectiveAlertConfig config = configService.resolve(reading.getTenantId(), reading.getPlanId(),
                plan != null ? plan.getProgrammeCode() : null);

        episodeService.openOrAttach(new OpenOrAttachCommand(
                reading.getTenantId(),
                reading.getPlanId(),
                reading.getPatientCpid(),
                reading.getDeviceRef(),
                AlertTriggerClass.THRESHOLD_BREACH,
                "PLAN:" + reading.getPlanId() + CLINICAL_GUARD_SUFFIX,
                capped,
                gated && raw.isAtLeast(AlertSeverity.RED),
                new ContributingReading(
                        reading.getId(), reading.getMetricCode(),
                        reading.getMetricValue().toPlainString(),
                        reading.getStatus().name(), trustLevel(reading),
                        reading.getMeasuredAt(), raw, gated),
                config,
                false));
    }

    // ── Trigger 9: persistent poor data quality from one device ─────────────────

    void evaluateDeviceQuality(TelemetryReadingEntity reading) {
        EffectiveAlertConfig config = configService.resolve(reading.getTenantId(),
                reading.getPlanId(), null);
        if (!config.deviceAlertsEnabled()) {
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(24);
        long recent = readingRepository.countByDeviceRefAndStatusInAndReceivedAtAfter(
                reading.getDeviceRef(),
                EnumSet.of(ReadingStatus.DEGRADED_VALID, ReadingStatus.QUARANTINED),
                cutoff);
        if (recent < config.deviceQualityMinCount()) {
            return; // not persistent yet (§14.6 trigger 9 is a pattern, not a one-off)
        }
        // §14.6 rate limit: ONE live device-quality episode per device — a reading storm
        // from a malfunctioning cuff collapses into this episode (quiet attach, no event
        // multiplication) instead of paging a district.
        episodeService.openOrAttach(new OpenOrAttachCommand(
                reading.getTenantId(),
                reading.getPlanId(),
                reading.getPatientCpid(),
                reading.getDeviceRef(),
                AlertTriggerClass.DEVICE_QUALITY,
                "DEV:" + reading.getDeviceRef() + ":DEVICE_QUALITY",
                AlertSeverity.AMBER,
                false,
                new ContributingReading(
                        reading.getId(), reading.getMetricCode(),
                        null, // device-quality episodes never carry clinical values
                        reading.getStatus().name(), trustLevel(reading),
                        reading.getMeasuredAt(), AlertSeverity.AMBER, true),
                config,
                true));
    }

    // ── Trigger 8: device offline (journey #65) — scheduled heartbeat sweep ────

    /**
     * Sweep ACTIVE device assignments whose device has been silent past the effective
     * offline tolerance: opens ONE device-offline episode per device (guard-limited)
     * and raises the rung-4 CHW task (journey #65: offline three days → CHW visit).
     *
     * @return number of episodes newly opened
     */
    public int sweepDeviceOffline(OffsetDateTime now) {
        int opened = 0;
        List<DeviceAssignmentEntity> active = assignmentRepository.findByStatus(DeviceAssignmentStatus.ACTIVE);
        for (DeviceAssignmentEntity assignment : active) {
            try {
                if (sweepAssignmentOffline(assignment, now)) {
                    opened++;
                }
            } catch (RuntimeException e) {
                log.error("Offline sweep failed for assignment {} — continuing: {}",
                        assignment.getId(), e.getMessage());
            }
        }
        return opened;
    }

    private boolean sweepAssignmentOffline(DeviceAssignmentEntity assignment, OffsetDateTime now) {
        MonitoringPlanEntity plan = planRepository.findById(assignment.getPlanId()).orElse(null);
        EffectiveAlertConfig config = configService.resolve(assignment.getTenantId(),
                assignment.getPlanId(), plan != null ? plan.getProgrammeCode() : null);
        if (!config.deviceAlertsEnabled()) {
            return false;
        }
        OffsetDateTime lastSeen = readingRepository
                .findFirstByDeviceRefOrderByMeasuredAtDesc(assignment.getDeviceRef())
                .map(TelemetryReadingEntity::getMeasuredAt)
                .orElse(assignment.getActivatedAt() != null ? assignment.getActivatedAt() : assignment.getAssignedAt());
        if (lastSeen == null || lastSeen.isAfter(now.minusHours(config.offlineToleranceHours()))) {
            return false; // still within heartbeat tolerance
        }
        String guard = "DEV:" + assignment.getDeviceRef() + ":DEVICE_OFFLINE";
        boolean alreadyLive = episodeService
                .list(assignment.getTenantId(), null, null, assignment.getDeviceRef()).stream()
                .anyMatch(e -> guard.equals(e.getLiveGuard()));
        AlertEpisodeEntity episode = episodeService.openOrAttach(new OpenOrAttachCommand(
                assignment.getTenantId(),
                assignment.getPlanId(),
                assignment.getPatientCpid(),
                assignment.getDeviceRef(),
                AlertTriggerClass.DEVICE_OFFLINE,
                guard,
                AlertSeverity.AMBER,
                false,
                null, // no contributing reading — the trigger IS the absence
                config,
                false));
        if (alreadyLive) {
            return false; // rate-limited: the existing episode carries the situation
        }
        episodeService.autoChwTask(episode.getId()); // journey #65 rung 4
        return true;
    }

    // ── §14.6 overdue-acknowledgement sweep ─────────────────────────────────────

    /** @return number of episodes auto-escalated for silence */
    public int sweepOverdueAcknowledgements(OffsetDateTime now) {
        int escalated = 0;
        List<AlertEpisodeEntity> overdue = episodeRepositoryOverdue(now);
        for (AlertEpisodeEntity episode : overdue) {
            try {
                MonitoringPlanEntity plan = episode.getPlanId() != null
                        ? planRepository.findById(episode.getPlanId()).orElse(null) : null;
                EffectiveAlertConfig config = configService.resolve(episode.getTenantId(),
                        episode.getPlanId(), plan != null ? plan.getProgrammeCode() : null);
                if (episodeService.escalateOverdue(episode.getId(), config)) {
                    escalated++;
                }
            } catch (RuntimeException e) {
                log.error("Overdue sweep failed for episode {} — continuing: {}",
                        episode.getId(), e.getMessage());
            }
        }
        return escalated;
    }

    private List<AlertEpisodeEntity> episodeRepositoryOverdue(OffsetDateTime now) {
        return episodeService.listOverdue(now);
    }

    // ── Band vocabulary ─────────────────────────────────────────────────────────

    private JsonNode bandFor(String parametersJson, String metricCode) {
        if (parametersJson == null || parametersJson.isBlank() || metricCode == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(parametersJson);
            JsonNode band = root.get(metricCode);
            return band != null && band.isObject() ? band : null;
        } catch (Exception e) {
            log.warn("Unparseable threshold parameters — evaluation skipped: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Band classification. Upper direction: value beyond {@code redMax} → EMERGENCY,
     * beyond {@code amberMax} → RED, beyond {@code greenMax} → AMBER. Lower direction
     * mirrors with {@code *Min}. {@code null} = inside the green band.
     */
    static AlertSeverity classify(BigDecimal value, JsonNode band) {
        AlertSeverity upper = classifyDirection(value, band, "greenMax", "amberMax", "redMax", true);
        AlertSeverity lower = classifyDirection(value, band, "greenMin", "amberMin", "redMin", false);
        if (upper == null) return lower;
        if (lower == null) return upper;
        return upper.isAtLeast(lower) ? upper : lower;
    }

    private static AlertSeverity classifyDirection(BigDecimal value, JsonNode band,
                                                   String green, String amber, String red, boolean upper) {
        BigDecimal greenBound = decimal(band, green);
        if (greenBound == null || !beyond(value, greenBound, upper)) {
            return null;
        }
        BigDecimal redBound = decimal(band, red);
        if (redBound != null && beyond(value, redBound, upper)) {
            return AlertSeverity.EMERGENCY;
        }
        BigDecimal amberBound = decimal(band, amber);
        if (amberBound != null && beyond(value, amberBound, upper)) {
            return AlertSeverity.RED;
        }
        return AlertSeverity.AMBER;
    }

    private static boolean beyond(BigDecimal value, BigDecimal bound, boolean upper) {
        return upper ? value.compareTo(bound) > 0 : value.compareTo(bound) < 0;
    }

    private static BigDecimal decimal(JsonNode band, String field) {
        JsonNode node = band.get(field);
        return node != null && node.isNumber() ? node.decimalValue() : null;
    }

    // ── Trust gate ──────────────────────────────────────────────────────────────

    private boolean isGated(TelemetryReadingEntity reading) {
        if (reading.getStatus() == ReadingStatus.DEGRADED_VALID) {
            return true;
        }
        String trust = trustLevel(reading);
        return trust != null && LOW_TRUST_GRADES.contains(trust.toUpperCase());
    }

    private String trustLevel(TelemetryReadingEntity reading) {
        String stamp = reading.getQualityStamp();
        if (stamp == null || stamp.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(stamp);
            JsonNode trust = node.get("trustLevel");
            return trust != null && !trust.isNull() ? trust.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
