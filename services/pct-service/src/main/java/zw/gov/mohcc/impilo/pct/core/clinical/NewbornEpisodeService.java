package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.NewbornBirthRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.NewbornBirthRecordRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens and maintains a newborn's own clinical episode.
 *
 * <p>A delivery has historically existed only on the mother's record: an intra-operative
 * event on her theatre episode, and a civil notification in UBOMI. Neither gives the baby
 * a clinical record of their own, and the consequence is concrete — an APGAR score or a
 * newborn examination recorded against the mother's encounter resolves to the mother's
 * CPID and is refused by the care-relationship guard. This service closes that gap by
 * giving the baby a birth record, a journey and an encounter under their own CPID, so
 * every subsequent clinical write about the child resolves to the child.</p>
 *
 * <p>Creation is idempotent on (tenant, baby CPID). A delivery event replayed by Kafka, or
 * a delivery captured from both theatre and a maternity form, must converge on one record;
 * two birth records for one baby would split the child's history at the moment it starts.
 * Twins have distinct CPIDs and therefore distinct records, which is why the key is the
 * baby rather than the delivery.</p>
 *
 * <p>What this service does not do is equally deliberate: it never mints an identity (VITO
 * owns that, and the CPID must already exist before a delivery is recorded), and it never
 * submits a civil birth notification (UBOMI owns that, and attesting a birth to the state
 * is a human act, not a side effect of a clinical event).</p>
 */
@Service
public class NewbornEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(NewbornEpisodeService.class);

    private final NewbornBirthRecordRepository birthRecordRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final NewbornEpisodeOpener episodeOpener;

    public NewbornEpisodeService(NewbornBirthRecordRepository birthRecordRepository,
                                 EventOutboxRepository outboxRepository,
                                 ObjectMapper objectMapper,
                                 NewbornEpisodeOpener episodeOpener) {
        this.birthRecordRepository = birthRecordRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.episodeOpener = episodeOpener;
    }

    @Transactional(readOnly = true)
    public Optional<NewbornBirthRecordEntity> findBySubject(String subjectCpid) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return birthRecordRepository.findByTenantIdAndSubjectCpid(tenantId, subjectCpid);
    }

    /** Gestational age at birth, which every later growth reading needs to correct for prematurity. */
    @Transactional(readOnly = true)
    public Integer gestationalAgeWeeks(String subjectCpid) {
        return findBySubject(subjectCpid)
                .map(NewbornBirthRecordEntity::getGestationalAgeWeeks)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<NewbornBirthRecordEntity> findByMother(String motherCpid) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return birthRecordRepository.findByTenantIdAndMotherCpidOrderByBornAtDesc(tenantId, motherCpid);
    }

    /**
     * Ensure the newborn has a birth record and an open clinical episode, creating them on
     * first sight and enriching them on later ones.
     *
     * @param body the delivery details, from a theatre event, a maternity form or a manual entry
     */
    @Transactional
    public NewbornBirthRecordEntity ensureNewbornEpisode(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();

        String babyCpid = firstNonBlank(
                str(body.get("subject_cpid")), str(body.get("baby_cpid")), str(body.get("babyCpid")));
        if (babyCpid == null) {
            throw new IllegalArgumentException(
                    "baby_cpid is required — a provisional VITO identity must exist before the birth is recorded");
        }

        Optional<NewbornBirthRecordEntity> existing =
                birthRecordRepository.findByTenantIdAndSubjectCpid(ctx.tenantId(), babyCpid);
        boolean isNew = existing.isEmpty();

        NewbornBirthRecordEntity record = existing.orElseGet(() -> {
            NewbornBirthRecordEntity fresh = new NewbornBirthRecordEntity();
            fresh.setBirthRecordId(UUID.randomUUID());
            fresh.setTenantId(ctx.tenantId());
            fresh.setSubjectCpid(babyCpid);
            fresh.setRecordedBy(firstNonBlank(str(body.get("recorded_by")), ctx.actorId()));
            return fresh;
        });

        applyDeliveryDetails(record, body, ctx);

        if (isNew) {
            openClinicalEpisode(record, ctx);
        }

        record.setUpdatedAt(OffsetDateTime.now());
        record = birthRecordRepository.save(record);

        if (isNew) {
            emit("pct.newborn.episode.opened", record);
            log.info("pct.newborn.episode.opened babyCpid={} motherCpid={} source={} journey={} encounter={} ga={} bw={}",
                    record.getSubjectCpid(), record.getMotherCpid(), record.getDeliverySource(),
                    record.getJourneyId(), record.getEncounterId(),
                    record.getGestationalAgeWeeks(), record.getBirthWeightGrams());
        } else {
            emit("pct.newborn.record.updated", record);
            log.info("pct.newborn.record.updated babyCpid={} source={} (existing record enriched, not duplicated)",
                    record.getSubjectCpid(), record.getDeliverySource());
        }
        return record;
    }

    /**
     * Opens the baby's journey and encounter, delegating to {@link NewbornEpisodeOpener} so the
     * attempt runs in its own transaction. A failure there must not lose the birth record — the
     * clinical facts of the delivery are worth more than the workflow scaffolding around them —
     * and catching the exception here would not be enough, because a failed insert marks the
     * surrounding transaction rollback-only and the birth record would be discarded at commit.
     */
    private void openClinicalEpisode(NewbornBirthRecordEntity record, TrustContext ctx) {
        UUID facilityId = record.getFacilityId() != null ? record.getFacilityId() : ctx.facilityId();
        NewbornEpisodeOpener.OpenedEpisode episode =
                episodeOpener.open(facilityId, record.getSubjectCpid(), record.getDeliverySourceRef());
        if (episode != null) {
            record.setJourneyId(episode.journeyId());
            record.setEncounterId(episode.encounterId());
        }
    }

    private void applyDeliveryDetails(NewbornBirthRecordEntity record, Map<String, Object> body, TrustContext ctx) {
        // Fields only ever fill gaps or carry new information; a later, less-informed source
        // must not blank out what an earlier one recorded.
        record.setMotherCpid(firstNonBlank(record.getMotherCpid(),
                str(body.get("mother_cpid")), str(body.get("motherCpid"))));
        if (record.getFacilityId() == null) {
            record.setFacilityId(firstNonNull(uuid(body.get("facility_id")), ctx.facilityId()));
        }
        record.setBornAt(firstNonNull(record.getBornAt(), timestamp(firstNonBlank(
                str(body.get("born_at")), str(body.get("delivery_time")), str(body.get("deliveryTime"))))));
        record.setSexAtBirth(firstNonBlank(record.getSexAtBirth(), upper(body.get("sex"))));

        record.setGestationalAgeWeeks(firstNonNull(record.getGestationalAgeWeeks(),
                integer(firstNonNullObject(body.get("gestational_age_weeks"), body.get("gestationalAgeWeeks")))));
        record.setGestationalAgeBasis(firstNonBlank(record.getGestationalAgeBasis(),
                upper(body.get("gestational_age_basis"))));
        record.setBirthWeightGrams(firstNonNull(record.getBirthWeightGrams(),
                integer(firstNonNullObject(body.get("birth_weight_grams"), body.get("birthWeightGrams")))));
        record.setBirthLengthCm(firstNonNull(record.getBirthLengthCm(), decimal(body.get("birth_length_cm"))));
        record.setHeadCircumferenceCm(firstNonNull(record.getHeadCircumferenceCm(),
                decimal(body.get("head_circumference_cm"))));
        record.setBirthOrder(firstNonNull(record.getBirthOrder(),
                integer(firstNonNullObject(body.get("birth_order"), body.get("birthOrder")))));
        if (bool(body.get("multiple_birth"))) {
            record.setMultipleBirth(true);
        }

        record.setDeliveryMode(firstNonBlank(record.getDeliveryMode(), upper(body.get("delivery_mode"))));
        record.setPlaceOfBirth(firstNonBlank(record.getPlaceOfBirth(), upper(body.get("place_of_birth"))));
        record.setBirthAttendant(firstNonBlank(record.getBirthAttendant(), str(body.get("birth_attendant"))));
        String outcome = upper(body.get("outcome"));
        if (outcome != null) {
            record.setBirthOutcome(outcome);
        }

        record.setApgarOneMinute(firstNonNull(record.getApgarOneMinute(),
                integer(firstNonNullObject(body.get("apgar_1"), body.get("apgar1")))));
        record.setApgarFiveMinute(firstNonNull(record.getApgarFiveMinute(),
                integer(firstNonNullObject(body.get("apgar_5"), body.get("apgar5")))));
        record.setApgarTenMinute(firstNonNull(record.getApgarTenMinute(),
                integer(firstNonNullObject(body.get("apgar_10"), body.get("apgar10")))));
        record.setResuscitation(firstNonBlank(record.getResuscitation(), upper(body.get("resuscitation"))));
        record.setResuscitationResponse(firstNonBlank(record.getResuscitationResponse(),
                str(body.get("resuscitation_response"))));

        record.setSkinToSkin(assertion(body.get("skin_to_skin"), record.getSkinToSkin()));
        record.setMinutesToFirstBreastfeed(firstNonNull(record.getMinutesToFirstBreastfeed(),
                integer(body.get("minutes_to_first_breastfeed"))));
        record.setVitaminKGiven(assertion(body.get("vitamin_k_given"), record.getVitaminKGiven()));
        record.setEyeProphylaxisGiven(assertion(body.get("eye_prophylaxis_given"), record.getEyeProphylaxisGiven()));
        record.setCordCare(firstNonBlank(record.getCordCare(), str(body.get("cord_care"))));
        record.setTemperatureCelsius(firstNonNull(record.getTemperatureCelsius(),
                decimal(body.get("temperature_celsius"))));

        String hivExposure = upper(body.get("hiv_exposure"));
        if (hivExposure != null) {
            record.setHivExposure(hivExposure);
        }
        record.setMaternalRiskFactors(firstNonBlank(record.getMaternalRiskFactors(),
                json(body.get("maternal_risk_factors"))));
        record.setNewbornExamination(firstNonBlank(record.getNewbornExamination(),
                json(body.get("newborn_examination"))));
        record.setCongenitalAnomalies(firstNonBlank(record.getCongenitalAnomalies(),
                json(body.get("congenital_anomalies"))));
        record.setBirthTrauma(firstNonBlank(record.getBirthTrauma(), json(body.get("birth_trauma"))));
        record.setDangerSigns(firstNonBlank(record.getDangerSigns(), json(body.get("danger_signs"))));

        record.setAdmissionDestination(firstNonBlank(record.getAdmissionDestination(),
                upper(firstNonNullObject(body.get("admission_destination"), body.get("destination")))));

        String source = upper(body.get("delivery_source"));
        if (source != null) {
            record.setDeliverySource(source);
        }
        record.setDeliverySourceRef(firstNonBlank(record.getDeliverySourceRef(),
                str(firstNonNullObject(body.get("delivery_source_ref"), body.get("episode_id")))));
        record.setUbomiNotificationRef(firstNonBlank(record.getUbomiNotificationRef(),
                str(body.get("ubomi_notification_ref"))));
    }

    private void emit(String eventType, NewbornBirthRecordEntity record) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("birthRecordId", record.getBirthRecordId().toString());
        payload.put("subjectCpid", record.getSubjectCpid());
        payload.put("motherCpid", record.getMotherCpid());
        payload.put("journeyId", record.getJourneyId());
        payload.put("encounterId", record.getEncounterId());
        payload.put("bornAt", record.getBornAt() != null ? record.getBornAt().toString() : null);
        payload.put("gestationalAgeWeeks", record.getGestationalAgeWeeks());
        payload.put("birthWeightGrams", record.getBirthWeightGrams());
        payload.put("preterm", record.isPreterm());
        payload.put("lowBirthWeight", record.isLowBirthWeight());
        payload.put("birthOutcome", record.getBirthOutcome());
        payload.put("admissionDestination", record.getAdmissionDestination());
        payload.put("deliverySource", record.getDeliverySource());
        payload.put("actorId", ctx.actorId());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("NEWBORN_BIRTH_RECORD");
        outbox.setAggregateId(record.getBirthRecordId().toString());
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialise newborn payload: {}", e.getMessage());
            return "{}";
        }
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text.isBlank() ? null : text;
        }
        return toJson(value);
    }

    /**
     * Three-state clinical assertion. An absent value leaves the existing state alone rather
     * than asserting that care was not given: silence is not a negative finding.
     */
    private static String assertion(Object value, String current) {
        String text = upper(value);
        if (text == null) {
            return current;
        }
        return switch (text) {
            case "DONE", "YES", "TRUE", "GIVEN" -> "DONE";
            case "NOT_DONE", "NO", "FALSE" -> "NOT_DONE";
            default -> "NOT_ASSESSED";
        };
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        String text = str(value);
        return text != null && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes"));
    }

    private static OffsetDateTime timestamp(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
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
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
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
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private static String upper(Object value) {
        String text = str(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object firstNonNullObject(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
