package zw.gov.mohcc.impilo.telemonitoring.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import zw.gov.mohcc.impilo.telemonitoring.core.DeviceAssignmentService.ResolvedAssignment;
import zw.gov.mohcc.impilo.telemonitoring.domain.CalibrationPosture;
import zw.gov.mohcc.impilo.telemonitoring.domain.DeviceAssignmentStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.ReadingStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.ShrWriteState;
import zw.gov.mohcc.impilo.telemonitoring.events.TelemonitoringEventEmitter;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.TelemetryReadingEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.TelemetryReadingRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * §14.5 step 11 — clinical consumption of the telemetry bus (OF-B25). Each ingested
 * reading passes, in order:
 *
 * <ol>
 *   <li><b>Dedup (journey #64, ingest half)</b> — sha-256(device|metric|measured_at);
 *       replays are audit-counted no-ops, backstopped by the DB unique constraint.</li>
 *   <li><b>The assignment gate (journey #62)</b> — {@link DeviceAssignmentService#resolveAssignment}:
 *       no live assignment → QUARANTINED {@code NO_ASSIGNMENT}; SUSPENDED assignment →
 *       QUARANTINED {@code SUSPENDED_ASSIGNMENT}; payload subject ≠ assignment subject →
 *       QUARANTINED {@code SUBJECT_MISMATCH}. Every quarantined row keeps patient/plan
 *       NULL (a wrong-record write is the failure criterion) and emits
 *       {@code telemonitoring.reading.quarantined.v1} — never silently dropped.</li>
 *   <li><b>Calibration stamp (§15.5/§15.7)</b> — posture ≠ OK → DEGRADED_VALID:
 *       stamped, kept, and gated from value-alerting later by OF-B26.</li>
 *   <li><b>Single-writer hand-off (step 14)</b> — VALID/DEGRADED_VALID readings under
 *       an ACTIVE plan go to {@link ObservationShrWriter}.</li>
 * </ol>
 */
@Service
public class ReadingIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ReadingIngestionService.class);

    public static final String REASON_NO_ASSIGNMENT = "NO_ASSIGNMENT";
    public static final String REASON_SUSPENDED_ASSIGNMENT = "SUSPENDED_ASSIGNMENT";
    public static final String REASON_SUBJECT_MISMATCH = "SUBJECT_MISMATCH";
    public static final String REASON_UNPARSEABLE_VALUE = "UNPARSEABLE_VALUE";

    /** One consumed bus payload, already syntactically valid (the consumer enforces that). */
    public record IngestCommand(
            UUID tenantId,
            String sourceReadingId,
            String deviceRef,
            String metricCode,
            String rawValue,
            String unit,
            OffsetDateTime measuredAt,
            String source,
            /** Patient the payload CLAIMS, when it claims one — checked against the assignment (#62). */
            String claimedPatientCpid) {
    }

    public record IngestOutcome(TelemetryReadingEntity reading, boolean deduplicated) {}

    private final TelemetryReadingRepository readingRepository;
    private final DeviceAssignmentService deviceAssignmentService;
    private final ObservationShrWriter shrWriter;
    private final TelemonitoringEventEmitter eventEmitter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final List<ReadingCompletionListener> completionListeners;
    private final Counter ingestedCounter;
    private final Counter dedupCounter;
    private final Counter quarantinedCounter;

    public ReadingIngestionService(TelemetryReadingRepository readingRepository,
                                   DeviceAssignmentService deviceAssignmentService,
                                   ObservationShrWriter shrWriter,
                                   TelemonitoringEventEmitter eventEmitter,
                                   ObjectMapper objectMapper,
                                   PlatformTransactionManager transactionManager,
                                   MeterRegistry meterRegistry,
                                   List<ReadingCompletionListener> completionListeners) {
        this.readingRepository = readingRepository;
        this.deviceAssignmentService = deviceAssignmentService;
        this.shrWriter = shrWriter;
        this.eventEmitter = eventEmitter;
        this.objectMapper = objectMapper;
        this.completionListeners = completionListeners;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.ingestedCounter = meterRegistry.counter("telemonitoring.readings.ingested");
        this.dedupCounter = meterRegistry.counter("telemonitoring.readings.deduplicated");
        this.quarantinedCounter = meterRegistry.counter("telemonitoring.readings.quarantined");
    }

    /**
     * Ingest one reading. Returns the stored row, or the pre-existing row flagged
     * {@code deduplicated} when the reading replayed (#64 — a no-op beyond the audit count).
     *
     * <p>Deliberately NOT transaction-per-method: the gate lookup runs in its own
     * (read-only) transaction — its TM_ASSIGNMENT_NOT_FOUND throw is a routine
     * quarantine signal and must not poison the persistence transaction — and the
     * row+event persist is wrapped atomically in {@code transactionTemplate}. The
     * SHR write attempt then runs in the writer's own transaction: a gateway failure
     * can never roll back an already-ingested reading.</p>
     */
    public IngestOutcome ingest(IngestCommand cmd) {
        String dedupKey = dedupKey(cmd.deviceRef(), cmd.metricCode(), cmd.measuredAt());

        // ── Journey #64: replayed readings are audit-counted no-ops ──
        if (readingRepository.existsByDedupKey(dedupKey)) {
            return dedupNoOp(dedupKey, cmd);
        }

        TelemetryReadingEntity reading = new TelemetryReadingEntity();
        reading.setTenantId(cmd.tenantId());
        reading.setSourceReadingId(cmd.sourceReadingId());
        reading.setDeviceRef(cmd.deviceRef());
        reading.setMetricCode(cmd.metricCode());
        reading.setUnit(cmd.unit());
        reading.setMeasuredAt(cmd.measuredAt());
        reading.setReceivedAt(OffsetDateTime.now());
        reading.setSource(cmd.source());
        reading.setDedupKey(dedupKey);

        // ── The assignment gate (#62) ──
        ResolvedAssignment assignment = resolveOrNull(cmd.deviceRef());
        Map<String, Object> stamp = new HashMap<>();
        stamp.put("source", cmd.source());
        // Trust grading is iot-ingestion's half of OF-B25 (policy model pending OD-14);
        // the bus payload carries no grade yet — stamped honestly as UNGRADED.
        stamp.put("trustLevel", "UNGRADED");

        String quarantineReason = null;
        if (assignment == null) {
            quarantineReason = REASON_NO_ASSIGNMENT;
            stamp.put("gate", "no live clinical assignment for device " + cmd.deviceRef());
        } else {
            stamp.put("assignmentId", assignment.assignmentId().toString());
            stamp.put("calibrationPosture", assignment.calibrationPosture().name());
            if (assignment.calibrationPostureReason() != null) {
                stamp.put("calibrationReason", assignment.calibrationPostureReason());
            }
            if (assignment.status() == DeviceAssignmentStatus.SUSPENDED) {
                quarantineReason = REASON_SUSPENDED_ASSIGNMENT;
            } else if (cmd.claimedPatientCpid() != null
                    && !cmd.claimedPatientCpid().equals(assignment.patientCpid())) {
                quarantineReason = REASON_SUBJECT_MISMATCH;
                stamp.put("claimedSubject", cmd.claimedPatientCpid());
                stamp.put("gate", "payload claims a subject that differs from the live assignment");
            }
        }

        // ── Value parse: an unusable value is retained as quarantined evidence, not dropped ──
        BigDecimal value = parseValue(cmd.rawValue());
        if (quarantineReason == null && value == null) {
            quarantineReason = REASON_UNPARSEABLE_VALUE;
            stamp.put("rawValue", cmd.rawValue());
        }
        reading.setMetricValue(value);

        if (quarantineReason != null) {
            // #62 invariant: quarantined rows carry NO patient attribution — the failure
            // criterion is a wrong-record write, so nothing attributable is stored.
            reading.setStatus(ReadingStatus.QUARANTINED);
            reading.setQuarantineReason(quarantineReason);
            reading.setShrWriteState(ShrWriteState.NOT_ELIGIBLE);
        } else {
            reading.setAssignmentId(assignment.assignmentId());
            reading.setPlanId(assignment.planId());
            reading.setPatientCpid(assignment.patientCpid());
            // DEGRADED/UNVERIFIED postures stamp the reading DEGRADED_VALID (§15.5 —
            // kept, gated from value-alerting by OF-B26). UNTRACKED means the assignment
            // simply has no physical-truth leg (e.g. BYOD pending OD-14): nothing negative
            // was found, so the reading stays VALID with the posture in its stamp.
            boolean degraded = assignment.calibrationPosture() == CalibrationPosture.DEGRADED
                    || assignment.calibrationPosture() == CalibrationPosture.UNVERIFIED;
            reading.setStatus(degraded ? ReadingStatus.DEGRADED_VALID : ReadingStatus.VALID);
            reading.setShrWriteState(ShrWriteState.PENDING);
        }
        reading.setQualityStamp(toJson(stamp));

        // Row + quarantine event persist atomically; the dedup constraint backstops races (#64).
        final TelemetryReadingEntity toStore = reading;
        final String reason = quarantineReason;
        IngestOutcome stored = transactionTemplate.execute(tx -> {
            TelemetryReadingEntity persisted;
            try {
                persisted = readingRepository.saveAndFlush(toStore);
            } catch (DataIntegrityViolationException e) {
                // Two consumers raced past the pre-flight check; the unique constraint held (#64).
                tx.setRollbackOnly();
                return null;
            }
            if (persisted.getStatus() == ReadingStatus.QUARANTINED) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("readingId", persisted.getId().toString());
                payload.put("deviceRef", persisted.getDeviceRef());
                payload.put("metricCode", persisted.getMetricCode());
                payload.put("reason", reason);
                payload.put("measuredAt", persisted.getMeasuredAt().toString());
                eventEmitter.emitReadingEvent("quarantined", persisted.getId(), persisted.getDeviceRef(),
                        payload, persisted.getTenantId());
            }
            return new IngestOutcome(persisted, false);
        });
        if (stored == null) {
            return dedupNoOp(dedupKey, cmd);
        }
        ingestedCounter.increment();

        TelemetryReadingEntity persisted = stored.reading();
        if (persisted.getStatus() == ReadingStatus.QUARANTINED) {
            quarantinedCounter.increment();
            log.warn("Reading {} QUARANTINED ({}) for device {} — retained, never dropped",
                    persisted.getId(), quarantineReason, persisted.getDeviceRef());
        } else {
            // ── Single-writer hand-off (§14.5 step 14) — the writer's own transaction ──
            shrWriter.attemptWrite(persisted);
        }

        // ── OF-B26 seam (§14.5 steps 15/16): alert evaluation AFTER ingestion completes.
        // Best-effort: a broken evaluator must never fail an already-ingested reading.
        for (ReadingCompletionListener listener : completionListeners) {
            try {
                listener.onReadingCompleted(persisted);
            } catch (RuntimeException e) {
                log.error("Reading-completion listener {} failed for reading {} — ingestion unaffected: {}",
                        listener.getClass().getSimpleName(), persisted.getId(), e.getMessage());
            }
        }
        return stored;
    }

    private IngestOutcome dedupNoOp(String dedupKey, IngestCommand cmd) {
        dedupCounter.increment();
        log.info("Duplicate reading dropped by dedup (device={}, metric={}, measuredAt={}) — journey #64 no-op",
                cmd.deviceRef(), cmd.metricCode(), cmd.measuredAt());
        TelemetryReadingEntity existing = readingRepository.findByDedupKey(dedupKey).orElse(null);
        return new IngestOutcome(existing, true);
    }

    /** The gate lookup: absent binding is a quarantine signal here, not an HTTP 404. */
    private ResolvedAssignment resolveOrNull(String deviceRef) {
        try {
            return deviceAssignmentService.resolveAssignment(deviceRef, null);
        } catch (TelemonitoringDomainException e) {
            if ("TM_ASSIGNMENT_NOT_FOUND".equals(e.getCode())) {
                return null;
            }
            throw e;
        }
    }

    static BigDecimal parseValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** #64 identity: device + metric + original device timestamp (instant-normalised). */
    static String dedupKey(String deviceRef, String metricCode, OffsetDateTime measuredAt) {
        String material = deviceRef + "|" + metricCode + "|" + measuredAt.toInstant().toEpochMilli();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("error", "stamp serialisation failed: " + e.getMessage());
            return fallback.toString();
        }
    }
}
