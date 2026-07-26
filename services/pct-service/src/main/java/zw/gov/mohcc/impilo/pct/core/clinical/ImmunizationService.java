package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImmunizationEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ImmunizationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immunisation administered-dose system of record.
 *
 * <p>Holds dose facts only: what was given, to whom, when, from which vial, by whom — or
 * the reason a dose was not given. It does not decide what is due. Keeping administration
 * separate from the schedule means a change in national immunisation policy is a governed
 * content release rather than a data migration, and a historical dose is never rewritten
 * when the schedule moves.</p>
 *
 * <p>Not-given reasons are kept distinct rather than collapsed into a single "missing"
 * state. A refusal, a contraindication, a deferral and a dose reported from elsewhere but
 * not yet verified all mean different things for what should happen next; treating them
 * alike is how children are counted as covered when they are not.</p>
 *
 * <p>A dose reported from elsewhere is recorded but does not count towards a series until
 * it is verified against a card or register — accepting hearsay as fact is how a child
 * misses a real dose.</p>
 */
@Service
public class ImmunizationService {

    private static final Logger log = LoggerFactory.getLogger(ImmunizationService.class);

    private static final Set<String> VALID_STATUSES = Set.of(
            ImmunizationEntity.STATUS_COMPLETED,
            ImmunizationEntity.STATUS_REFUSED,
            ImmunizationEntity.STATUS_CONTRAINDICATED,
            ImmunizationEntity.STATUS_DEFERRED,
            ImmunizationEntity.STATUS_PENDING_VERIFICATION,
            ImmunizationEntity.STATUS_ENTERED_IN_ERROR);

    private static final Set<String> VALID_DELIVERY_CONTEXTS =
            Set.of("ROUTINE", "CAMPAIGN", "OUTREACH", "SCHOOL");

    private final ImmunizationRepository immunizationRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;

    public ImmunizationService(ImmunizationRepository immunizationRepository,
                               EventOutboxRepository outboxRepository,
                               ObjectMapper objectMapper,
                               ClinicalAccessGuard accessGuard) {
        this.immunizationRepository = immunizationRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public Page<ImmunizationEntity> list(String subjectCpid, int page, int size) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return immunizationRepository.findByTenantIdAndSubjectCpidOrderByOccurrenceAtDesc(
                tenantId, subjectCpid, PageRequest.of(Math.max(0, page), size < 1 ? 20 : Math.min(size, 200)));
    }

    /** The dose history a forecast is evaluated against: administered doses, oldest first. */
    @Transactional(readOnly = true)
    public List<ImmunizationEntity> doseHistory(String subjectCpid) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return immunizationRepository.findByTenantIdAndSubjectCpidOrderByOccurrenceAtAsc(tenantId, subjectCpid)
                .stream()
                .filter(ImmunizationEntity::countsTowardsSeries)
                .toList();
    }

    @Transactional
    public ImmunizationEntity record(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();

        String subject = firstNonBlank(str(body.get("subject_cpid")), str(body.get("patient_id")));
        if (subject == null) {
            throw new IllegalArgumentException("Missing field: patient_id");
        }
        accessGuard.requireCareRelationship(ctx, subject, str(body.get("journey_id")), str(body.get("encounter_id")));

        String vaccineName = str(body.get("vaccine_name"));
        if (vaccineName == null) {
            throw new IllegalArgumentException("Missing field: vaccine_name");
        }

        ImmunizationEntity row = new ImmunizationEntity();
        row.setImmunizationId(UUID.randomUUID());
        row.setTenantId(ctx.tenantId());
        row.setSubjectCpid(subject);
        row.setJourneyId(str(body.get("journey_id")));
        row.setEncounterId(str(body.get("encounter_id")));
        row.setFacilityId(uuid(body.get("facility_id")));

        row.setVaccineName(vaccineName);
        row.setVaccineCode(str(body.get("vaccine_code")));
        row.setCodeSystem(str(body.get("code_system")));
        row.setDoseNumber(integer(body.get("dose_number")));
        // The legacy BFF contract calls this dose_sequence; the clinical term is series.
        row.setSeries(firstNonBlank(str(body.get("series")), str(body.get("dose_sequence"))));
        row.setOccurrenceAt(parseTimestamp(firstNonBlank(
                str(body.get("occurrence_at")), str(body.get("administered_at")))));

        row.setLotNumber(str(body.get("lot_number")));
        row.setExpiryDate(parseDate(firstNonBlank(
                str(body.get("expiry_date")), str(body.get("expiration_date")))));
        row.setDiluentLotNumber(str(body.get("diluent_lot_number")));

        row.setDoseQuantity(str(body.get("dose_quantity")));
        row.setRoute(upper(body.get("route")));
        row.setBodySite(firstNonBlank(str(body.get("body_site")), str(body.get("site"))));
        row.setAdministeredBy(firstNonBlank(str(body.get("administered_by")), ctx.actorId()));

        String deliveryContext = upperOr(body.get("delivery_context"), "ROUTINE");
        if (!VALID_DELIVERY_CONTEXTS.contains(deliveryContext)) {
            throw new IllegalArgumentException("Unknown delivery_context: " + deliveryContext);
        }
        row.setDeliveryContext(deliveryContext);
        row.setCampaignRef(str(body.get("campaign_ref")));
        row.setOutreachLocation(str(body.get("outreach_location")));

        String status = upperOr(body.get("status"), ImmunizationEntity.STATUS_COMPLETED);
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unknown immunisation status: " + status);
        }
        row.setStatus(status);
        row.setStatusReason(str(body.get("status_reason")));
        row.setDeferredUntil(parseDate(str(body.get("deferred_until"))));
        row.setSourceReference(str(body.get("source_reference")));
        row.setAefiCaseReference(str(body.get("aefi_case_reference")));

        // A dose that was not given needs a stated reason, otherwise the record cannot tell
        // a clinician what to do next — offer again, wait, or never give this vaccine.
        if (!ImmunizationEntity.STATUS_COMPLETED.equals(status)
                && !ImmunizationEntity.STATUS_PENDING_VERIFICATION.equals(status)
                && row.getStatusReason() == null) {
            throw new IllegalArgumentException("status_reason is required when a dose was not given");
        }

        row.setNotes(str(body.get("notes")));
        row.setRecordedBy(firstNonBlank(str(body.get("recorded_by")), ctx.actorId()));

        row = immunizationRepository.save(row);
        emit("pct.immunization.recorded", row);

        log.info("pct.immunization.recorded id={} subject={} vaccine={} dose={} status={} context={} by={} correlationId={}",
                row.getImmunizationId(), row.getSubjectCpid(), row.getVaccineName(), row.getDoseNumber(),
                row.getStatus(), row.getDeliveryContext(), ctx.actorId(), ctx.correlationId());
        return row;
    }

    /**
     * Confirms a dose reported from elsewhere against a card or register, at which point it
     * starts counting towards the series.
     */
    @Transactional
    public ImmunizationEntity verify(UUID immunizationId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        ImmunizationEntity row = immunizationRepository.findById(immunizationId)
                .orElseThrow(() -> new IllegalArgumentException("Immunisation not found: " + immunizationId));
        if (!row.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Immunisation not found: " + immunizationId);
        }
        if (!ImmunizationEntity.STATUS_PENDING_VERIFICATION.equals(row.getStatus())) {
            throw new IllegalArgumentException(
                    "Only a dose recorded elsewhere and pending verification can be verified");
        }
        String sourceReference = firstNonBlank(str(body.get("source_reference")), row.getSourceReference());
        if (sourceReference == null) {
            throw new IllegalArgumentException("source_reference is required to verify a reported dose");
        }
        row.setSourceReference(sourceReference);
        row.setStatus(ImmunizationEntity.STATUS_COMPLETED);
        row.setVerifiedBy(ctx.actorId());
        row.setVerifiedAt(OffsetDateTime.now());
        row.setUpdatedAt(OffsetDateTime.now());
        row = immunizationRepository.save(row);

        emit("pct.immunization.verified", row);
        log.info("pct.immunization.verified id={} subject={} source={} by={} correlationId={}",
                row.getImmunizationId(), row.getSubjectCpid(), sourceReference, ctx.actorId(), ctx.correlationId());
        return row;
    }

    private void emit(String eventType, ImmunizationEntity row) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("immunizationId", row.getImmunizationId().toString());
        payload.put("subjectCpid", row.getSubjectCpid());
        payload.put("encounterId", row.getEncounterId());
        payload.put("vaccineCode", row.getVaccineCode());
        payload.put("vaccineName", row.getVaccineName());
        payload.put("doseNumber", row.getDoseNumber());
        payload.put("series", row.getSeries());
        payload.put("occurrenceAt", row.getOccurrenceAt().toString());
        payload.put("status", row.getStatus());
        payload.put("deliveryContext", row.getDeliveryContext());
        payload.put("lotNumber", row.getLotNumber());
        payload.put("actorId", ctx.actorId());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("IMMUNIZATION");
        outbox.setAggregateId(row.getImmunizationId().toString());
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialise immunisation event payload: {}", e.getMessage());
            return "{}";
        }
    }

    private static OffsetDateTime parseTimestamp(String value) {
        if (value == null) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            LocalDate date = parseDate(value);
            if (date != null) {
                return date.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
            }
            throw new IllegalArgumentException("occurrence_at is not a valid timestamp: " + value);
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID uuid(Object value) {
        String text = str(value);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private static String upper(Object value) {
        String text = str(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private static String upperOr(Object value, String fallback) {
        String text = upper(value);
        return text != null ? text : fallback;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
