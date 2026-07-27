package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ObservationEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ObservationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * System of record for discrete clinical observations — the spine under the forms, growth,
 * danger-sign and vitals surfaces.
 *
 * <p>One write path. Everything that produces an observation — a nurse typing a temperature, a
 * form extraction, a device feed — lands here, so the access check, the value/absence rule and
 * the event are applied once rather than re-implemented per caller.</p>
 *
 * <h2>A row must assert something</h2>
 * <p>An observation carries a value, or it carries the reason there is none
 * ({@code NOT_ASSESSED}, {@code REFUSED}, {@code UNABLE_TO_OBTAIN}…). Neither is rejected with a
 * 400 rather than allowed to reach the database CHECK constraint, because a constraint violation
 * tells the caller nothing about what it did wrong. The absence itself is clinical information:
 * "the temperature was not taken" is a different fact from "the temperature is unremarkable",
 * and the danger-sign and classification engines refuse to blur the two.</p>
 *
 * <h2>Corrections, not duplicates</h2>
 * <p>When the body carries provenance ({@code derived_from_type} + {@code derived_from_id}) and
 * a row already exists for that source and code, the existing row is amended in place and marked
 * {@code AMENDED}. Re-submitting a form corrects what it previously asserted instead of leaving
 * two rows that disagree — the same rule the {@code uq_pct_observations_derived} unique index
 * enforces at the storage layer.</p>
 *
 * <h2>The event carries no PII</h2>
 * <p>{@code pct.observation.recorded} is consumed into the shared health record, which is
 * CPID-only by doctrine. The payload therefore carries the subject's CPID and the clinical fact
 * and nothing that identifies the person — no name, date of birth, address, phone or national
 * ID. Any such field arriving in the request body is used for nothing and never republished.</p>
 */
@Service
public class ObservationService {

    private static final Logger log = LoggerFactory.getLogger(ObservationService.class);

    private final ObservationRepository observationRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;

    public ObservationService(ObservationRepository observationRepository,
                              EventOutboxRepository outboxRepository,
                              ObjectMapper objectMapper,
                              ClinicalAccessGuard accessGuard) {
        this.observationRepository = observationRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    /**
     * Observations for a subject, newest first. Narrowed by {@code code} (one clinical question
     * over time) or by {@code encounterId} (everything observed at one contact) when supplied.
     * The subject filter is applied in every branch: an encounter id from the caller never
     * widens the read beyond the patient it was asked for.
     */
    @Transactional(readOnly = true)
    public List<ObservationEntity> list(String subjectCpid, String code, String encounterId) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        if (subjectCpid == null || subjectCpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient_id is required");
        }

        if (encounterId != null && !encounterId.isBlank()) {
            return observationRepository
                    .findByTenantIdAndEncounterIdOrderByEffectiveAtDesc(tenantId, encounterId.trim())
                    .stream()
                    .filter(row -> subjectCpid.equals(row.getSubjectCpid()))
                    .filter(row -> code == null || code.isBlank() || code.equals(row.getCode()))
                    .toList();
        }
        if (code != null && !code.isBlank()) {
            return observationRepository.findByTenantIdAndSubjectCpidAndCodeOrderByEffectiveAtDesc(
                    tenantId, subjectCpid, code.trim());
        }
        return observationRepository.findByTenantIdAndSubjectCpidOrderByEffectiveAtDesc(tenantId, subjectCpid);
    }

    /**
     * The single write path. Accepts snake_case or camelCase keys because the BFF, the form
     * extractor and the offline pack each send a different dialect of the same contract.
     */
    @Transactional
    public ObservationEntity record(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        if (body == null || body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "an observation body is required");
        }

        String subject = str(body.get("subject_cpid"), body.get("subjectCpid"),
                body.get("patient_id"), body.get("patientId"));
        if (subject == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient_id is required");
        }
        String journeyId = str(body.get("journey_id"), body.get("journeyId"));
        String encounterId = str(body.get("encounter_id"), body.get("encounterId"));

        // Dimension 6 of the access model — subject relationship. Nothing is written before this.
        accessGuard.requireCareRelationship(ctx, subject, journeyId, encounterId);

        String code = str(body.get("code"), body.get("observation_code"), body.get("observationCode"));
        if (code == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "code is required: an observation without a code asserts a fact nothing can read");
        }

        BigDecimal valueQuantity = decimal(body.get("value_quantity"), body.get("valueQuantity"));
        String valueUnit = str(body.get("value_unit"), body.get("valueUnit"));
        String valueCode = str(body.get("value_code"), body.get("valueCode"));
        Boolean valueBoolean = bool(body.get("value_boolean"), body.get("valueBoolean"));
        String valueText = str(body.get("value_text"), body.get("valueText"));
        String dataAbsentReason = upper(str(body.get("data_absent_reason"), body.get("dataAbsentReason")));

        // A row that carries neither a value nor a reason claims to be an observation and observes
        // nothing. The DB CHECK would catch it, but a constraint violation tells the caller nothing.
        boolean hasValue = valueQuantity != null || valueCode != null || valueBoolean != null || valueText != null;
        if (!hasValue && dataAbsentReason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An observation must either carry a value (value_quantity, value_code, value_boolean "
                            + "or value_text) or state why it has none (data_absent_reason: NOT_ASSESSED, "
                            + "ASKED_BUT_UNKNOWN, REFUSED, UNABLE_TO_OBTAIN, NOT_PERFORMED). A row with "
                            + "neither records nothing while looking like a record.");
        }
        // A quantity without a unit is not a measurement — 37 of what?
        if (valueQuantity != null && valueUnit == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "value_unit is required when value_quantity is supplied: a quantity without a unit "
                            + "is not a measurement.");
        }

        String derivedFromType = upper(str(body.get("derived_from_type"), body.get("derivedFromType")));
        String derivedFromId = str(body.get("derived_from_id"), body.get("derivedFromId"));

        // Provenance match: correct the row this source already produced rather than duplicate it.
        ObservationEntity row = null;
        boolean amendment = false;
        if (derivedFromType != null && derivedFromId != null) {
            Optional<ObservationEntity> existing = observationRepository
                    .findByTenantIdAndDerivedFromTypeAndDerivedFromIdAndCode(
                            ctx.tenantId(), derivedFromType, derivedFromId, code);
            if (existing.isPresent()) {
                row = existing.get();
                amendment = true;
            }
        }
        if (row == null) {
            row = new ObservationEntity();
            row.setObservationId(UUID.randomUUID());
            row.setTenantId(ctx.tenantId());
        }

        row.setSubjectCpid(subject);
        row.setJourneyId(journeyId);
        row.setEncounterId(encounterId);
        row.setFacilityId(firstUuid(uuid(body.get("facility_id"), body.get("facilityId")), ctx.facilityId()));

        row.setCode(code);
        row.setCodeSystem(orDefault(str(body.get("code_system"), body.get("codeSystem")),
                "impilo-clinical-observation"));
        row.setDisplay(str(body.get("display"), body.get("display_name"), body.get("displayName")));
        row.setCategory(orDefault(upper(str(body.get("category"))), "EXAM"));

        row.setEffectiveAt(timestamp(body, "effective_at", "effectiveAt", "observed_at", "observedAt"));
        row.setRecordedBy(orDefault(str(body.get("recorded_by"), body.get("recordedBy")), ctx.actorId()));
        row.setRecordedAt(OffsetDateTime.now());

        row.setValueQuantity(valueQuantity);
        row.setValueUnit(valueUnit);
        row.setValueCode(valueCode);
        row.setValueCodeSystem(str(body.get("value_code_system"), body.get("valueCodeSystem")));
        row.setValueBoolean(valueBoolean);
        row.setValueText(valueText);
        row.setDataAbsentReason(dataAbsentReason);

        row.setInterpretation(upper(str(body.get("interpretation"))));
        row.setReferenceRangeLow(decimal(body.get("reference_range_low"), body.get("referenceRangeLow")));
        row.setReferenceRangeHigh(decimal(body.get("reference_range_high"), body.get("referenceRangeHigh")));
        row.setReferenceRangeSource(str(body.get("reference_range_source"), body.get("referenceRangeSource")));

        row.setSource(orDefault(upper(str(body.get("source"))), "MANUAL"));
        row.setDerivedFromType(derivedFromType);
        row.setDerivedFromId(derivedFromId);
        row.setDeviceRef(str(body.get("device_ref"), body.get("deviceRef")));
        row.setNotes(str(body.get("notes")));

        // An amended row states that it was amended, whatever the caller asked for: the correction
        // is part of the clinical record, not an implementation detail of the re-submission.
        row.setStatus(amendment ? "AMENDED" : orDefault(upper(str(body.get("status"))), "FINAL"));

        ObservationEntity saved = observationRepository.save(row);
        emit(saved, ctx);

        log.info("pct.observation.recorded id={} subject={} code={} status={} absent={} by={} correlationId={}",
                saved.getObservationId(), saved.getSubjectCpid(), saved.getCode(), saved.getStatus(),
                saved.getDataAbsentReason(), ctx.actorId(), ctx.correlationId());
        return saved;
    }

    /**
     * Records a set of observations taken at the same contact. One transaction: a partially
     * applied vitals set is worse than a rejected one, because the missing readings would be
     * indistinguishable from questions never asked.
     */
    @Transactional
    public List<ObservationEntity> recordBatch(List<Map<String, Object>> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "observations must be a non-empty list");
        }
        List<ObservationEntity> saved = new ArrayList<>(bodies.size());
        for (Map<String, Object> body : bodies) {
            saved.add(record(body));
        }
        return saved;
    }

    /**
     * Voids an observation — the record-honest form of deletion. The row is never removed:
     * {@code ENTERED_IN_ERROR} states that the fact was asserted and withdrawn, which is itself
     * clinical information (a danger-sign engine that consumed the value needs to know it was
     * wrong, not to have never seen it). Idempotent: re-voiding a voided row is a no-op.
     */
    @Transactional
    public ObservationEntity voidObservation(String observationId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        UUID id;
        try {
            id = UUID.fromString(observationId == null ? "" : observationId.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "observation id must be a UUID");
        }
        ObservationEntity row = observationRepository.findByTenantIdAndObservationId(ctx.tenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "observation not found"));
        if ("ENTERED_IN_ERROR".equals(row.getStatus())) {
            return row;
        }
        // Same discipline as the write path: withdrawing a clinical assertion is a clinical write.
        accessGuard.requireCareRelationship(ctx, row.getSubjectCpid(),
                row.getJourneyId(), row.getEncounterId());

        row.setStatus("ENTERED_IN_ERROR");
        if (reason != null && !reason.isBlank()) {
            String existing = row.getNotes();
            row.setNotes((existing == null || existing.isBlank() ? "" : existing + " | ")
                    + "voided: " + reason.trim());
        }
        ObservationEntity saved = observationRepository.save(row);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("observation_id", saved.getObservationId().toString());
        payload.put("subject_cpid", saved.getSubjectCpid());
        payload.put("tenant_id", saved.getTenantId() != null ? saved.getTenantId().toString() : null);
        payload.put("code", saved.getCode());
        payload.put("status", saved.getStatus());
        payload.put("reason", reason);
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("OBSERVATION");
        outbox.setAggregateId(saved.getObservationId().toString());
        outbox.setEventType("pct.observation.voided");
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);

        log.info("pct.observation.voided id={} subject={} code={} by={} correlationId={}",
                saved.getObservationId(), saved.getSubjectCpid(), saved.getCode(),
                ctx.actorId(), ctx.correlationId());
        return saved;
    }

    /**
     * The event the shared health record consumes. CPID-only by doctrine: the clinical fact and
     * the person's pseudonymous identifier, never anything that identifies the person.
     */
    private void emit(ObservationEntity row, TrustContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("observation_id", row.getObservationId().toString());
        payload.put("subject_cpid", row.getSubjectCpid());
        payload.put("tenant_id", row.getTenantId() != null ? row.getTenantId().toString() : null);
        payload.put("journey_id", row.getJourneyId());
        payload.put("encounter_id", row.getEncounterId());
        payload.put("code", row.getCode());
        payload.put("code_system", row.getCodeSystem());
        payload.put("display", row.getDisplay());
        payload.put("category", row.getCategory());
        payload.put("effective_at", row.getEffectiveAt() != null ? row.getEffectiveAt().toString() : null);
        payload.put("value_quantity", row.getValueQuantity());
        payload.put("value_unit", row.getValueUnit());
        payload.put("value_code", row.getValueCode());
        payload.put("value_code_system", row.getValueCodeSystem());
        payload.put("value_boolean", row.getValueBoolean());
        payload.put("value_text", row.getValueText());
        payload.put("data_absent_reason", row.getDataAbsentReason());
        payload.put("interpretation", row.getInterpretation());
        payload.put("status", row.getStatus());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("OBSERVATION");
        outbox.setAggregateId(row.getObservationId().toString());
        outbox.setEventType("pct.observation.recorded");
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    /** Wire shape for the API, snake_case to match the contract the BFF already sends. */
    public static Map<String, Object> toMap(ObservationEntity row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("observation_id", row.getObservationId() != null ? row.getObservationId().toString() : null);
        m.put("patient_id", row.getSubjectCpid());
        m.put("subject_cpid", row.getSubjectCpid());
        m.put("journey_id", row.getJourneyId());
        m.put("encounter_id", row.getEncounterId());
        m.put("facility_id", row.getFacilityId() != null ? row.getFacilityId().toString() : null);

        m.put("code", row.getCode());
        m.put("code_system", row.getCodeSystem());
        m.put("display", row.getDisplay());
        m.put("category", row.getCategory());

        m.put("effective_at", row.getEffectiveAt() != null ? row.getEffectiveAt().toString() : null);
        m.put("recorded_by", row.getRecordedBy());
        m.put("recorded_at", row.getRecordedAt() != null ? row.getRecordedAt().toString() : null);

        m.put("value_quantity", row.getValueQuantity());
        m.put("value_unit", row.getValueUnit());
        m.put("value_code", row.getValueCode());
        m.put("value_code_system", row.getValueCodeSystem());
        m.put("value_boolean", row.getValueBoolean());
        m.put("value_text", row.getValueText());
        m.put("data_absent_reason", row.getDataAbsentReason());

        m.put("interpretation", row.getInterpretation());
        m.put("reference_range_low", row.getReferenceRangeLow());
        m.put("reference_range_high", row.getReferenceRangeHigh());
        m.put("reference_range_source", row.getReferenceRangeSource());

        m.put("source", row.getSource());
        m.put("derived_from_type", row.getDerivedFromType());
        m.put("derived_from_id", row.getDerivedFromId());
        m.put("device_ref", row.getDeviceRef());

        m.put("status", row.getStatus());
        m.put("notes", row.getNotes());
        return m;
    }

    // --- helpers ---------------------------------------------------------------------------

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise observation payload", e);
        }
    }

    private static OffsetDateTime timestamp(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            String value = str(body.get(key));
            if (value != null) {
                try {
                    return OffsetDateTime.parse(value);
                } catch (RuntimeException ignored) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            key + " must be an ISO-8601 timestamp with an offset");
                }
            }
        }
        return OffsetDateTime.now();
    }

    private static String str(Object... candidates) {
        for (Object value : candidates) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
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
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "not a valid observation quantity: " + value);
                }
            }
        }
        return null;
    }

    /**
     * Only an explicit true/false counts. An unparseable token is rejected rather than silently
     * read as false — "convulsions: maybe" must not become "convulsions: no".
     */
    private static Boolean bool(Object... candidates) {
        for (Object value : candidates) {
            if (value instanceof Boolean b) {
                return b;
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
                if (text.equals("true") || text.equals("yes")) {
                    return Boolean.TRUE;
                }
                if (text.equals("false") || text.equals("no")) {
                    return Boolean.FALSE;
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "value_boolean must be true or false, got: " + value);
            }
        }
        return null;
    }

    private static UUID uuid(Object... candidates) {
        String text = str(candidates);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID firstUuid(UUID preferred, UUID fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
