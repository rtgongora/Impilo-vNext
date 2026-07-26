package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.persistence.entity.CtgAnnotationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.CtgChunkEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.CtgSessionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.LabourObservationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PartographSessionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.CtgAnnotationRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.CtgChunkRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.CtgSessionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.LabourObservationRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PartographSessionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Labour monitoring, the partograph and cardiotocography — the maternity system of record.
 *
 * <p>Closes a broken vertical. experience-bff and the maternity UI have called
 * {@code /v1/labour-monitoring} and {@code /v1/maternity/**} for some time; none of it existed
 * here, the BFF turned the 404 into an empty 200, and the CTG "stream" was an in-process map that
 * emptied on pod restart. Nothing a midwife recorded was ever stored.</p>
 *
 * <p>A partograph point and a labour observation are the same clinical fact, so they share one
 * table and one write path. The only difference is whether a session was open at the time.</p>
 */
@Service
public class MaternityService {

    private static final Logger log = LoggerFactory.getLogger(MaternityService.class);

    private static final String ACTIVE = "ACTIVE";
    private static final String CLOSED = "CLOSED";

    private final LabourObservationRepository observationRepository;
    private final PartographSessionRepository partographRepository;
    private final CtgSessionRepository ctgSessionRepository;
    private final CtgChunkRepository ctgChunkRepository;
    private final CtgAnnotationRepository ctgAnnotationRepository;
    private final EventOutboxRepository outboxRepository;
    private final ClinicalAccessGuard accessGuard;
    private final ObjectMapper objectMapper;

    public MaternityService(LabourObservationRepository observationRepository,
                            PartographSessionRepository partographRepository,
                            CtgSessionRepository ctgSessionRepository,
                            CtgChunkRepository ctgChunkRepository,
                            CtgAnnotationRepository ctgAnnotationRepository,
                            EventOutboxRepository outboxRepository,
                            ClinicalAccessGuard accessGuard,
                            ObjectMapper objectMapper) {
        this.observationRepository = observationRepository;
        this.partographRepository = partographRepository;
        this.ctgSessionRepository = ctgSessionRepository;
        this.ctgChunkRepository = ctgChunkRepository;
        this.ctgAnnotationRepository = ctgAnnotationRepository;
        this.outboxRepository = outboxRepository;
        this.accessGuard = accessGuard;
        this.objectMapper = objectMapper;
    }

    // --- labour observations -------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LabourObservationEntity> listObservations(String subjectCpid, String encounterId) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        if (encounterId != null && !encounterId.isBlank()) {
            return observationRepository
                    .findByTenantIdAndSubjectCpidAndEncounterIdOrderByObservedAtDesc(tenantId, subjectCpid, encounterId);
        }
        return observationRepository.findByTenantIdAndSubjectCpidOrderByObservedAtDesc(tenantId, subjectCpid);
    }

    /**
     * Records one labour observation. When a partograph session is open for this woman the
     * observation joins it automatically: a midwife recording the fetal heart should not have to
     * know whether a chart was started, and an observation stranded outside the session would be
     * invisible on the graph that decisions are made from.
     */
    @Transactional
    public LabourObservationEntity recordObservation(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String subject = subjectOf(body);
        String journeyId = str(body.get("journey_id"), body.get("journeyId"));
        String encounterId = str(body.get("encounter_id"), body.get("encounterId"));
        accessGuard.requireCareRelationship(ctx, subject, journeyId, encounterId);

        LabourObservationEntity row = new LabourObservationEntity();
        row.setObservationId(UUID.randomUUID());
        row.setTenantId(ctx.tenantId());
        row.setSubjectCpid(subject);
        row.setJourneyId(journeyId);
        row.setEncounterId(encounterId);
        row.setFacilityId(ctx.facilityId());
        row.setObservedAt(timestamp(body, "recorded_at", "recordedAt", "observed_at", "observedAt"));
        row.setRecordedBy(recordedBy(body, ctx));
        row.setPhase(orDefault(str(body.get("phase")), "ACTIVE_LABOUR"));

        applyClinicalFields(row, body);

        partographRepository.findByTenantIdAndSubjectCpidAndStatus(ctx.tenantId(), subject, ACTIVE)
                .ifPresent(session -> row.setPartographSessionId(session.getSessionId()));

        LabourObservationEntity saved = observationRepository.save(row);
        emit("LABOUR_OBSERVATION", saved.getObservationId().toString(), "pct.labour.observation.recorded",
                Map.of("subject_cpid", subject,
                        "observed_at", String.valueOf(saved.getObservedAt()),
                        "partograph_session_id", String.valueOf(saved.getPartographSessionId())), ctx);
        return saved;
    }

    private void applyClinicalFields(LabourObservationEntity row, Map<String, Object> body) {
        row.setFetalHeartRateBpm(integer(body.get("fetal_heart_rate_bpm"), body.get("fetalHeartRateBpm")));
        row.setLiquor(str(body.get("liquor")));
        row.setMoulding(str(body.get("moulding")));
        row.setCaput(str(body.get("caput")));
        row.setCervicalDilationCm(decimal(body.get("cervical_dilation_cm"), body.get("cervicalDilationCm")));
        row.setFetalDescentFifths(integer(body.get("fetal_descent_fifths"), body.get("fetalDescentFifths")));
        row.setContractionFrequency10min(
                integer(body.get("contraction_frequency_10min"), body.get("contractionFrequency10Min")));
        row.setContractionDurationSec(
                integer(body.get("contraction_duration_sec"), body.get("contractionDurationSec")));
        row.setMaternalPulseBpm(integer(body.get("maternal_pulse_bpm"), body.get("maternalPulseBpm")));
        row.setSystolicBp(integer(body.get("systolic_bp"), body.get("systolicBp")));
        row.setDiastolicBp(integer(body.get("diastolic_bp"), body.get("diastolicBp")));
        row.setTemperatureC(decimal(body.get("temperature_c"), body.get("temperatureC")));
        row.setUrineVolumeMl(integer(body.get("urine_volume_ml"), body.get("urineVolumeMl")));
        row.setUrineProtein(str(body.get("urine_protein"), body.get("urineProtein")));
        row.setUrineAcetone(str(body.get("urine_acetone"), body.get("urineAcetone")));
        row.setMaternalCondition(str(body.get("maternal_condition"), body.get("maternalCondition")));
        row.setOxytocinRateMiuMin(decimal(body.get("oxytocin_rate_miu_min"), body.get("oxytocinRateMiuMin")));
        row.setNotes(str(body.get("notes")));
    }

    // --- partograph ----------------------------------------------------------------------

    @Transactional
    public PartographSessionEntity openPartograph(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String subject = subjectOf(body);
        String journeyId = str(body.get("journey_id"), body.get("journeyId"));
        String encounterId = str(body.get("encounter_id"), body.get("encounterId"));
        accessGuard.requireCareRelationship(ctx, subject, journeyId, encounterId);

        // Reopening returns the session already running rather than starting a rival graph.
        Optional<PartographSessionEntity> existing =
                partographRepository.findByTenantIdAndSubjectCpidAndStatus(ctx.tenantId(), subject, ACTIVE);
        if (existing.isPresent()) {
            log.info("Partograph already open for subject={}, returning session={}",
                    subject, existing.get().getSessionId());
            return existing.get();
        }

        PartographSessionEntity session = new PartographSessionEntity();
        session.setSessionId(UUID.randomUUID());
        session.setTenantId(ctx.tenantId());
        session.setSubjectCpid(subject);
        session.setJourneyId(journeyId);
        session.setEncounterId(encounterId);
        session.setFacilityId(ctx.facilityId());
        session.setStatus(ACTIVE);
        session.setLabourPhase(orDefault(str(body.get("labour_phase"), body.get("labourPhase")), "ACTIVE_LABOUR"));
        session.setStartedAt(timestamp(body, "started_at", "startedAt"));
        session.setStartedBy(recordedBy(body, ctx));
        session.setPartographContentVersion(PartographProgressEngine.CONTENT_VERSION);

        PartographSessionEntity saved = partographRepository.save(session);
        emit("PARTOGRAPH_SESSION", saved.getSessionId().toString(), "pct.partograph.opened",
                Map.of("subject_cpid", subject, "started_at", String.valueOf(saved.getStartedAt())), ctx);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<PartographSessionEntity> activePartograph(String subjectCpid) {
        return partographRepository.findByTenantIdAndSubjectCpidAndStatus(
                TrustContextHolder.require().tenantId(), subjectCpid, ACTIVE);
    }

    @Transactional(readOnly = true)
    public PartographSessionEntity requirePartograph(UUID sessionId) {
        PartographSessionEntity session = partographRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "partograph session not found"));
        requireSameTenant(session.getTenantId());
        return session;
    }

    @Transactional(readOnly = true)
    public List<LabourObservationEntity> partographPoints(UUID sessionId) {
        return observationRepository.findByPartographSessionIdOrderByObservedAtAsc(sessionId);
    }

    /**
     * Adds a point to an open partograph. The point is a labour observation like any other, so it
     * goes through the same write path — a partograph drawn from a different table than the
     * labour record could disagree with it about the same woman.
     */
    @Transactional
    public LabourObservationEntity addPartographPoint(UUID sessionId, Map<String, Object> body) {
        PartographSessionEntity session = requirePartograph(sessionId);
        if (!ACTIVE.equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "partograph session is closed; open a new session to continue recording");
        }
        TrustContext ctx = TrustContextHolder.require();
        accessGuard.requireCareRelationship(ctx, session.getSubjectCpid(),
                session.getJourneyId(), session.getEncounterId());

        LabourObservationEntity row = new LabourObservationEntity();
        row.setObservationId(UUID.randomUUID());
        row.setTenantId(ctx.tenantId());
        row.setSubjectCpid(session.getSubjectCpid());
        row.setJourneyId(session.getJourneyId());
        row.setEncounterId(session.getEncounterId());
        row.setFacilityId(session.getFacilityId());
        row.setPartographSessionId(session.getSessionId());
        row.setObservedAt(timestamp(body, "observed_at", "observedAt", "recorded_at", "recordedAt"));
        row.setRecordedBy(recordedBy(body, ctx));
        row.setPhase(orDefault(str(body.get("phase")), session.getLabourPhase()));
        applyClinicalFields(row, body);

        LabourObservationEntity saved = observationRepository.save(row);

        // The alert line's starting point is pinned the first time active labour is recorded, and
        // never moved afterwards — a line that shifted when a later reading was corrected would
        // silently rewrite whether this labour had ever crossed it.
        if (session.getAlertReferenceAt() == null
                && saved.getCervicalDilationCm() != null
                && saved.getCervicalDilationCm().compareTo(PartographProgressEngine.ACTIVE_PHASE_DILATION_CM) >= 0) {
            session.setAlertReferenceAt(saved.getObservedAt());
            session.setAlertReferenceDilationCm(saved.getCervicalDilationCm());
            session.setUpdatedAt(OffsetDateTime.now());
            partographRepository.save(session);
        }

        emit("LABOUR_OBSERVATION", saved.getObservationId().toString(), "pct.partograph.point.added",
                Map.of("subject_cpid", session.getSubjectCpid(),
                        "session_id", session.getSessionId().toString(),
                        "observed_at", String.valueOf(saved.getObservedAt())), ctx);
        return saved;
    }

    @Transactional
    public PartographSessionEntity closePartograph(UUID sessionId, Map<String, Object> body) {
        PartographSessionEntity session = requirePartograph(sessionId);
        TrustContext ctx = TrustContextHolder.require();
        if (CLOSED.equals(session.getStatus())) {
            return session;
        }
        session.setStatus(CLOSED);
        session.setClosedAt(timestamp(body, "closed_at", "closedAt"));
        session.setClosedBy(recordedBy(body, ctx));
        session.setOutcome(str(body.get("outcome")));
        session.setSummaryNotes(str(body.get("summary_notes"), body.get("summaryNotes")));
        session.setUpdatedAt(OffsetDateTime.now());
        PartographSessionEntity saved = partographRepository.save(session);
        emit("PARTOGRAPH_SESSION", sessionId.toString(), "pct.partograph.closed",
                Map.of("subject_cpid", session.getSubjectCpid(),
                        "outcome", String.valueOf(session.getOutcome())), ctx);
        return saved;
    }

    /**
     * The assessed partograph: the stored points run through the WHO alert/action line engine.
     * Assessment happens on read rather than at write because it is a function of the whole
     * session, and every point added changes it.
     */
    @Transactional(readOnly = true)
    public PartographProgressEngine.Assessment assessPartograph(UUID sessionId, OffsetDateTime asOf) {
        List<PartographProgressEngine.Observation> points = partographPoints(sessionId).stream()
                .map(o -> new PartographProgressEngine.Observation(
                        o.getObservedAt(), o.getCervicalDilationCm(), o.getFetalHeartRateBpm(),
                        o.getMaternalPulseBpm(), o.getSystolicBp(), o.getTemperatureC()))
                .toList();
        return PartographProgressEngine.assess(points, asOf);
    }

    // --- cardiotocography ------------------------------------------------------------------

    @Transactional
    public CtgSessionEntity openCtgSession(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String subject = subjectOf(body);
        String journeyId = str(body.get("journey_id"), body.get("journeyId"));
        String encounterId = str(body.get("encounter_id"), body.get("encounterId"));
        accessGuard.requireCareRelationship(ctx, subject, journeyId, encounterId);

        Optional<CtgSessionEntity> existing =
                ctgSessionRepository.findByTenantIdAndSubjectCpidAndStatus(ctx.tenantId(), subject, ACTIVE);
        if (existing.isPresent()) {
            return existing.get();
        }

        CtgSessionEntity session = new CtgSessionEntity();
        session.setSessionId(UUID.randomUUID());
        session.setTenantId(ctx.tenantId());
        session.setSubjectCpid(subject);
        session.setJourneyId(journeyId);
        session.setEncounterId(encounterId);
        session.setFacilityId(ctx.facilityId());
        session.setStatus(ACTIVE);
        session.setMonitoringMode(orDefault(str(body.get("monitoring_mode"), body.get("monitoringMode")),
                "EXTERNAL_CTG"));
        session.setDeviceId(str(body.get("device_id"), body.get("deviceId")));
        session.setStartedAt(timestamp(body, "started_at", "startedAt"));
        session.setStartedBy(recordedBy(body, ctx));
        session.setBaselineFhrBpm(integer(body.get("baseline_fhr_bpm"), body.get("baselineFhrBpm")));
        session.setBaselineMaternalHrBpm(
                integer(body.get("baseline_maternal_hr_bpm"), body.get("baselineMaternalHrBpm")));

        CtgSessionEntity saved = ctgSessionRepository.save(session);
        emit("CTG_SESSION", saved.getSessionId().toString(), "pct.ctg.session.opened",
                Map.of("subject_cpid", subject, "device_id", String.valueOf(saved.getDeviceId())), ctx);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<CtgSessionEntity> activeCtgSession(String subjectCpid) {
        return ctgSessionRepository.findByTenantIdAndSubjectCpidAndStatus(
                TrustContextHolder.require().tenantId(), subjectCpid, ACTIVE);
    }

    @Transactional(readOnly = true)
    public CtgSessionEntity requireCtgSession(UUID sessionId) {
        CtgSessionEntity session = ctgSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CTG session not found"));
        requireSameTenant(session.getTenantId());
        return session;
    }

    @Transactional(readOnly = true)
    public List<CtgChunkEntity> ctgChunks(UUID sessionId, String channel) {
        requireCtgSession(sessionId);
        return channel == null || channel.isBlank()
                ? ctgChunkRepository.findBySessionIdOrderByStartedAtAsc(sessionId)
                : ctgChunkRepository.findBySessionIdAndChannelOrderByStartedAtAsc(sessionId, channel);
    }

    /**
     * Stores a trace segment. The declared sample count is checked against the array actually
     * supplied: a chunk claiming 1,800 samples while carrying 1,200 has a gap, and the gap is
     * recorded rather than smoothed away, because a flat line the transducer never measured is
     * indistinguishable on screen from a flat line it did.
     */
    @Transactional
    public CtgChunkEntity addCtgChunk(UUID sessionId, Map<String, Object> body) {
        CtgSessionEntity session = requireCtgSession(sessionId);
        if (!ACTIVE.equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CTG session is closed");
        }
        TrustContext ctx = TrustContextHolder.require();
        accessGuard.requireCareRelationship(ctx, session.getSubjectCpid(),
                session.getJourneyId(), session.getEncounterId());

        Object samples = body.get("samples") != null ? body.get("samples") : body.get("samples_json");
        if (!(samples instanceof List<?> sampleList)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "samples must be an array");
        }
        String channel = str(body.get("channel"));
        if (channel == null || channel.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel is required");
        }
        BigDecimal sampleRate = decimal(body.get("sample_rate_hz"), body.get("sampleRateHz"));
        if (sampleRate == null || sampleRate.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sample_rate_hz must be positive");
        }

        Integer declared = integer(body.get("sample_count"), body.get("sampleCount"));
        int actual = sampleList.size();
        long missing = Math.max(0, (declared == null ? actual : declared) - actual);

        CtgChunkEntity chunk = new CtgChunkEntity();
        chunk.setChunkId(UUID.randomUUID());
        chunk.setSessionId(sessionId);
        chunk.setTenantId(ctx.tenantId());
        chunk.setSubjectCpid(session.getSubjectCpid());
        chunk.setChannel(channel);
        chunk.setStartedAt(timestamp(body, "started_at", "startedAt"));
        chunk.setEndedAt(optionalTimestamp(body, "ended_at", "endedAt"));
        chunk.setSampleRateHz(sampleRate);
        chunk.setSampleCount(actual);
        chunk.setMissingSampleCount((int) missing);
        chunk.setDurationSeconds(BigDecimal.valueOf(actual)
                .divide(sampleRate, 3, java.math.RoundingMode.HALF_UP));
        chunk.setUnit(orDefault(str(body.get("unit")), "BPM"));
        chunk.setSamplesJson(toJson(sampleList));
        chunk.setCapturedBy(recordedBy(body, ctx));
        chunk.setDeviceId(orDefault(str(body.get("device_id"), body.get("deviceId")), session.getDeviceId()));
        chunk.setNotes(str(body.get("notes")));

        CtgChunkEntity saved = ctgChunkRepository.save(chunk);
        if (missing > 0) {
            log.warn("CTG chunk {} on session {} declared {} samples but carried {} — {} recorded as missing",
                    saved.getChunkId(), sessionId, declared, actual, missing);
        }
        emit("CTG_CHUNK", saved.getChunkId().toString(), "pct.ctg.chunk.recorded",
                Map.of("session_id", sessionId.toString(), "channel", channel,
                        "sample_count", String.valueOf(actual),
                        "missing_sample_count", String.valueOf(missing)), ctx);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CtgAnnotationEntity> ctgAnnotations(UUID sessionId) {
        requireCtgSession(sessionId);
        return ctgAnnotationRepository.findBySessionIdOrderByRecordedAtAsc(sessionId);
    }

    @Transactional
    public CtgAnnotationEntity addCtgAnnotation(UUID sessionId, Map<String, Object> body) {
        CtgSessionEntity session = requireCtgSession(sessionId);
        TrustContext ctx = TrustContextHolder.require();
        accessGuard.requireCareRelationship(ctx, session.getSubjectCpid(),
                session.getJourneyId(), session.getEncounterId());

        String category = str(body.get("category"));
        if (category == null || category.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category is required");
        }

        CtgAnnotationEntity annotation = new CtgAnnotationEntity();
        annotation.setAnnotationId(UUID.randomUUID());
        annotation.setSessionId(sessionId);
        annotation.setTenantId(ctx.tenantId());
        annotation.setSubjectCpid(session.getSubjectCpid());
        annotation.setRecordedAt(timestamp(body, "recorded_at", "recordedAt"));
        annotation.setCategory(category);
        annotation.setChannel(str(body.get("channel")));
        annotation.setSampleOffsetSec(decimal(body.get("sample_offset_sec"), body.get("sampleOffsetSec")));
        annotation.setValue(str(body.get("value")));
        annotation.setSeverity(str(body.get("severity")));
        annotation.setNotes(str(body.get("notes")));
        annotation.setRecordedBy(recordedBy(body, ctx));

        CtgAnnotationEntity saved = ctgAnnotationRepository.save(annotation);
        emit("CTG_ANNOTATION", saved.getAnnotationId().toString(), "pct.ctg.annotation.recorded",
                Map.of("session_id", sessionId.toString(), "category", category,
                        "severity", String.valueOf(saved.getSeverity())), ctx);
        return saved;
    }

    // --- helpers ---------------------------------------------------------------------------

    private String subjectOf(Map<String, Object> body) {
        String subject = str(body.get("subject_cpid"), body.get("subjectCpid"),
                body.get("patient_id"), body.get("patientId"));
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient_id is required");
        }
        return subject;
    }

    private void requireSameTenant(UUID tenantId) {
        if (!TrustContextHolder.require().tenantId().equals(tenantId)) {
            // Reported as not-found rather than forbidden: confirming the id exists in another
            // tenant would itself leak that a woman is in labour somewhere.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }
    }

    private String recordedBy(Map<String, Object> body, TrustContext ctx) {
        String declared = str(body.get("recorded_by"), body.get("recordedBy"),
                body.get("started_by"), body.get("startedBy"),
                body.get("closed_by"), body.get("closedBy"),
                body.get("captured_by"), body.get("capturedBy"));
        return declared != null && !declared.isBlank() ? declared : ctx.actorId();
    }

    private void emit(String aggregateType, String aggregateId, String eventType,
                      Map<String, Object> payload, TrustContext ctx) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise maternity payload", e);
        }
    }

    private static OffsetDateTime timestamp(Map<String, Object> body, String... keys) {
        OffsetDateTime parsed = optionalTimestamp(body, keys);
        return parsed != null ? parsed : OffsetDateTime.now();
    }

    private static OffsetDateTime optionalTimestamp(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            String value = str(body.get(key));
            if (value != null && !value.isBlank()) {
                try {
                    return OffsetDateTime.parse(value);
                } catch (RuntimeException ignored) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            key + " must be an ISO-8601 timestamp with an offset");
                }
            }
        }
        return null;
    }

    private static String str(Object... candidates) {
        for (Object value : candidates) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static Integer integer(Object... candidates) {
        for (Object value : candidates) {
            if (value instanceof Number n) {
                return n.intValue();
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                try {
                    return Integer.valueOf(String.valueOf(value).trim());
                } catch (NumberFormatException ignored) {
                    // fall through to the next candidate key
                }
            }
        }
        return null;
    }

    private static BigDecimal decimal(Object... candidates) {
        for (Object value : candidates) {
            if (value instanceof Number n) {
                return new BigDecimal(n.toString());
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                try {
                    return new BigDecimal(String.valueOf(value).trim());
                } catch (NumberFormatException ignored) {
                    // fall through to the next candidate key
                }
            }
        }
        return null;
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    /** Summary for the maternity dashboard: the open session, its assessment, and recent points. */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(String subjectCpid, OffsetDateTime asOf) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("patient_id", subjectCpid);

        Optional<PartographSessionEntity> partograph = activePartograph(subjectCpid);
        out.put("partograph_active", partograph.isPresent());
        partograph.ifPresent(session -> {
            out.put("partograph_session_id", session.getSessionId().toString());
            PartographProgressEngine.Assessment assessment = assessPartograph(session.getSessionId(), asOf);
            Map<String, Object> progress = new LinkedHashMap<>();
            progress.put("status", assessment.status().name());
            progress.put("latest_dilation_cm", assessment.latestDilationCm());
            progress.put("expected_dilation_cm", assessment.expectedDilationCm());
            progress.put("hours_behind_alert_line", assessment.hoursBehindAlertLine());
            progress.put("outstanding_observations", assessment.outstandingObservations());
            progress.put("observations", assessment.observations());
            progress.put("recommended_action", assessment.recommendedAction());
            progress.put("content_version", assessment.contentVersion());
            progress.put("content_source", assessment.contentSource());
            out.put("progress", progress);
        });

        Optional<CtgSessionEntity> ctg = activeCtgSession(subjectCpid);
        out.put("ctg_active", ctg.isPresent());
        ctg.ifPresent(session -> out.put("ctg_session_id", session.getSessionId().toString()));

        List<LabourObservationEntity> recent = listObservations(subjectCpid, null);
        out.put("observation_count", recent.size());
        out.put("last_observed_at", recent.isEmpty() ? null : String.valueOf(recent.get(0).getObservedAt()));
        return out;
    }

    /** Serialisation lives here so the controller and the summary agree on field names. */
    public static Map<String, Object> toMap(LabourObservationEntity row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("observation_id", row.getObservationId().toString());
        m.put("patient_id", row.getSubjectCpid());
        m.put("journey_id", row.getJourneyId());
        m.put("encounter_id", row.getEncounterId());
        m.put("partograph_session_id",
                row.getPartographSessionId() == null ? null : row.getPartographSessionId().toString());
        m.put("observed_at", String.valueOf(row.getObservedAt()));
        m.put("recorded_by", row.getRecordedBy());
        m.put("phase", row.getPhase());
        m.put("fetal_heart_rate_bpm", row.getFetalHeartRateBpm());
        m.put("liquor", row.getLiquor());
        m.put("moulding", row.getMoulding());
        m.put("caput", row.getCaput());
        m.put("cervical_dilation_cm", row.getCervicalDilationCm());
        m.put("fetal_descent_fifths", row.getFetalDescentFifths());
        m.put("contraction_frequency_10min", row.getContractionFrequency10min());
        m.put("contraction_duration_sec", row.getContractionDurationSec());
        m.put("maternal_pulse_bpm", row.getMaternalPulseBpm());
        m.put("systolic_bp", row.getSystolicBp());
        m.put("diastolic_bp", row.getDiastolicBp());
        m.put("temperature_c", row.getTemperatureC());
        m.put("urine_volume_ml", row.getUrineVolumeMl());
        m.put("urine_protein", row.getUrineProtein());
        m.put("urine_acetone", row.getUrineAcetone());
        m.put("maternal_condition", row.getMaternalCondition());
        m.put("oxytocin_rate_miu_min", row.getOxytocinRateMiuMin());
        m.put("notes", row.getNotes());
        return m;
    }

    public static Map<String, Object> toMap(PartographSessionEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("session_id", s.getSessionId().toString());
        m.put("patient_id", s.getSubjectCpid());
        m.put("journey_id", s.getJourneyId());
        m.put("encounter_id", s.getEncounterId());
        m.put("status", s.getStatus());
        m.put("labour_phase", s.getLabourPhase());
        m.put("started_at", String.valueOf(s.getStartedAt()));
        m.put("started_by", s.getStartedBy());
        m.put("closed_at", s.getClosedAt() == null ? null : String.valueOf(s.getClosedAt()));
        m.put("closed_by", s.getClosedBy());
        m.put("outcome", s.getOutcome());
        m.put("summary_notes", s.getSummaryNotes());
        m.put("alert_reference_at",
                s.getAlertReferenceAt() == null ? null : String.valueOf(s.getAlertReferenceAt()));
        m.put("alert_reference_dilation_cm", s.getAlertReferenceDilationCm());
        m.put("content_version", s.getPartographContentVersion());
        return m;
    }

    public static Map<String, Object> toMap(CtgSessionEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("session_id", s.getSessionId().toString());
        m.put("patient_id", s.getSubjectCpid());
        m.put("journey_id", s.getJourneyId());
        m.put("encounter_id", s.getEncounterId());
        m.put("status", s.getStatus());
        m.put("monitoring_mode", s.getMonitoringMode());
        m.put("device_id", s.getDeviceId());
        m.put("started_at", String.valueOf(s.getStartedAt()));
        m.put("started_by", s.getStartedBy());
        m.put("closed_at", s.getClosedAt() == null ? null : String.valueOf(s.getClosedAt()));
        m.put("baseline_fhr_bpm", s.getBaselineFhrBpm());
        m.put("baseline_maternal_hr_bpm", s.getBaselineMaternalHrBpm());
        m.put("summary_notes", s.getSummaryNotes());
        return m;
    }

    public Map<String, Object> chunkToMap(CtgChunkEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chunk_id", c.getChunkId().toString());
        m.put("session_id", c.getSessionId().toString());
        m.put("channel", c.getChannel());
        m.put("started_at", String.valueOf(c.getStartedAt()));
        m.put("ended_at", c.getEndedAt() == null ? null : String.valueOf(c.getEndedAt()));
        m.put("sample_rate_hz", c.getSampleRateHz());
        m.put("sample_count", c.getSampleCount());
        m.put("missing_sample_count", c.getMissingSampleCount());
        m.put("duration_seconds", c.getDurationSeconds());
        m.put("unit", c.getUnit());
        m.put("captured_by", c.getCapturedBy());
        m.put("device_id", c.getDeviceId());
        m.put("notes", c.getNotes());
        try {
            m.put("samples", objectMapper.readValue(c.getSamplesJson(), List.class));
        } catch (JsonProcessingException e) {
            // Return the trace metadata with the samples explicitly absent rather than failing the
            // whole read: a reviewer still needs to see that the segment exists.
            log.error("CTG chunk {} has unreadable samples_json: {}", c.getChunkId(), e.getMessage());
            m.put("samples", null);
            m.put("samples_error", "stored trace could not be decoded");
        }
        return m;
    }

    public static Map<String, Object> toMap(CtgAnnotationEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("annotation_id", a.getAnnotationId().toString());
        m.put("session_id", a.getSessionId().toString());
        m.put("recorded_at", String.valueOf(a.getRecordedAt()));
        m.put("category", a.getCategory());
        m.put("channel", a.getChannel());
        m.put("sample_offset_sec", a.getSampleOffsetSec());
        m.put("value", a.getValue());
        m.put("severity", a.getSeverity());
        m.put("notes", a.getNotes());
        m.put("recorded_by", a.getRecordedBy());
        return m;
    }

    public static List<Map<String, Object>> observationsToMaps(List<LabourObservationEntity> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        rows.forEach(r -> out.add(toMap(r)));
        return out;
    }
}
