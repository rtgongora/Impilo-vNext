package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.paediatrics.growth.GrowthEngine;
import zw.gov.mohcc.impilo.paediatrics.growth.GrowthIndicator;
import zw.gov.mohcc.impilo.paediatrics.growth.GrowthTrajectoryAnalyser;
import zw.gov.mohcc.impilo.paediatrics.growth.NutritionClassifier;
import zw.gov.mohcc.impilo.pct.integration.VitoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.GrowthMeasurementEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.NewbornBirthRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.GrowthMeasurementRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.NewbornBirthRecordRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Growth measurement system of record for the paediatric pathway.
 *
 * <p>Recording a measurement does three things in one transaction: it stores what was
 * measured and under what conditions, it scores the measurement against whichever growth
 * standard the child is eligible for, and it draws a nutrition conclusion from MUAC, oedema
 * and weight-for-age. Scoring happens on write rather than on read so that a measurement
 * taken offline carries its own interpretation, and so a later change to the standard or to
 * the recorded date of birth cannot silently restate a historical reading.</p>
 *
 * <p>The standard is chosen by the engine from gestational age: term children are read on
 * the WHO 2006 standard and infants born preterm on the Fenton 2013 preterm chart until
 * they grow off it. Gestational age therefore decides the clinical meaning of every score
 * here, which is why it is resolved from the child's birth record rather than relying on
 * whichever screen happened to submit the measurement knowing it.</p>
 *
 * <p>Scoring never blocks recording. If the child's date of birth or sex is unknown, or the
 * child is older than the loaded standard covers, the measurement is still stored and the
 * reason no score could be produced is stored with it. A missing z-score is a stated gap,
 * never an implied normal.</p>
 */
@Service
public class GrowthService {

    private static final Logger log = LoggerFactory.getLogger(GrowthService.class);

    private final GrowthMeasurementRepository growthRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;
    private final VitoIntegration vitoIntegration;
    private final GrowthEngine growthEngine;
    private final NewbornBirthRecordRepository newbornRepository;

    public GrowthService(GrowthMeasurementRepository growthRepository,
                         EventOutboxRepository outboxRepository,
                         ObjectMapper objectMapper,
                         ClinicalAccessGuard accessGuard,
                         VitoIntegration vitoIntegration,
                         GrowthEngine growthEngine,
                         NewbornBirthRecordRepository newbornRepository) {
        this.growthRepository = growthRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
        this.vitoIntegration = vitoIntegration;
        this.growthEngine = growthEngine;
        this.newbornRepository = newbornRepository;
    }

    @Transactional(readOnly = true)
    public List<GrowthMeasurementEntity> list(String subjectCpid) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return growthRepository.findByTenantIdAndSubjectCpidOrderByMeasuredAtDesc(tenantId, subjectCpid);
    }

    /**
     * Reads the stored measurements as a trajectory, which is where growth faltering shows
     * up long before any single measurement looks abnormal.
     */
    @Transactional(readOnly = true)
    public GrowthTrajectoryAnalyser.TrajectoryAssessment trajectory(String subjectCpid) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        List<GrowthTrajectoryAnalyser.TrajectoryPoint> points =
                growthRepository.findByTenantIdAndSubjectCpidOrderByMeasuredAtAsc(tenantId, subjectCpid)
                        .stream()
                        .map(row -> new GrowthTrajectoryAnalyser.TrajectoryPoint(
                                row.getAgeDays(), row.getWeightKg(), row.getWeightForAgeZ()))
                        .toList();
        return GrowthTrajectoryAnalyser.analyse(points);
    }

    @Transactional
    public GrowthMeasurementEntity record(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();

        String subject = firstNonBlank(str(body.get("subject_cpid")), str(body.get("patient_id")));
        if (subject == null) {
            throw new IllegalArgumentException("Missing field: patient_id");
        }
        accessGuard.requireCareRelationship(ctx, subject, str(body.get("journey_id")), str(body.get("encounter_id")));

        GrowthMeasurementEntity row = new GrowthMeasurementEntity();
        row.setGrowthId(UUID.randomUUID());
        row.setTenantId(ctx.tenantId());
        row.setSubjectCpid(subject);
        row.setJourneyId(str(body.get("journey_id")));
        row.setEncounterId(str(body.get("encounter_id")));
        row.setFacilityId(uuid(body.get("facility_id")));
        row.setMeasuredAt(parseTimestamp(str(body.get("measured_at"))));
        row.setRecordedBy(firstNonBlank(str(body.get("recorded_by")), ctx.actorId()));

        row.setWeightKg(decimal(body.get("weight_kg")));
        row.setLengthCm(decimal(body.get("length_cm")));
        row.setHeightCm(decimal(body.get("height_cm")));
        row.setHeadCircumferenceCm(decimal(body.get("head_circumference_cm")));
        row.setMuacCm(decimal(body.get("muac_cm")));
        row.setOedema(upperOr(body.get("oedema"), "NOT_ASSESSED"));

        row.setMeasurementMode(upper(body.get("measurement_mode")));
        row.setPosture(upper(body.get("posture")));
        row.setEquipmentRef(str(body.get("equipment_ref")));
        row.setClothing(upper(body.get("clothing")));
        row.setObserverCadre(str(body.get("observer_cadre")));
        row.setMeasurementConfidence(upperOr(body.get("measurement_confidence"), "MEASURED"));
        row.setNotes(str(body.get("notes")));

        if (row.getWeightKg() == null && row.getLengthCm() == null && row.getHeightCm() == null
                && row.getHeadCircumferenceCm() == null && row.getMuacCm() == null) {
            throw new IllegalArgumentException("At least one measurement is required");
        }

        score(row, body);

        row = growthRepository.save(row);
        emit(row);

        log.info("pct.growth.recorded id={} subject={} ageDays={} waz={} nutrition={} by={} correlationId={}",
                row.getGrowthId(), row.getSubjectCpid(), row.getAgeDays(), row.getWeightForAgeZ(),
                row.getNutritionStatus(), ctx.actorId(), ctx.correlationId());
        return row;
    }

    /**
     * Stamps age, z-scores and nutrition status onto the row. Any failure to resolve
     * the child's demographics degrades to an explained absence of scores, never to a
     * rejected measurement: losing the measurement is worse than losing the score.
     */
    private void score(GrowthMeasurementEntity row, Map<String, Object> body) {
        LocalDate dateOfBirth = null;
        String sex = null;

        Map<String, Object> demographics = resolveDemographics(row.getSubjectCpid());
        if (demographics != null) {
            dateOfBirth = parseDate(firstNonBlank(
                    str(demographics.get("dateOfBirth")),
                    str(demographics.get("dob")),
                    str(demographics.get("birthDate"))));
            sex = firstNonBlank(str(demographics.get("sex")), str(demographics.get("gender")));
        }
        // A caller that already holds the facts (an offline pack, a birth episode) may supply
        // them; supplied values only fill gaps, they never override the identity service.
        if (dateOfBirth == null) {
            dateOfBirth = parseDate(str(body.get("date_of_birth")));
        }
        if (sex == null) {
            sex = str(body.get("sex"));
        }

        // Which standard applies turns entirely on gestational age, so it is resolved from
        // the birth record when the caller does not carry it. A growth screen has no reason
        // to know a baby's gestation, but the newborn record does — and without this the
        // preterm chart could never be selected outside the neonatal unit that recorded it.
        Integer gestationalAgeWeeks = integer(body.get("gestational_age_weeks"));
        String gestationalAgeSource = gestationalAgeWeeks != null ? "SUPPLIED" : null;
        if (gestationalAgeWeeks == null) {
            gestationalAgeWeeks = gestationalAgeFromBirthRecord(row.getSubjectCpid());
            gestationalAgeSource = gestationalAgeWeeks != null ? "NEWBORN_BIRTH_RECORD" : "NOT_RECORDED";
        }
        row.setGestationalAgeWeeks(gestationalAgeWeeks);
        row.setGestationalAgeSource(gestationalAgeSource);

        GrowthEngine.GrowthAssessment assessment = growthEngine.assess(
                new GrowthEngine.PatientContext(dateOfBirth, sex, gestationalAgeWeeks),
                new GrowthEngine.GrowthMeasurement(
                        row.getMeasuredAt(),
                        row.getWeightKg(),
                        row.getLengthCm(),
                        row.getHeightCm(),
                        row.getHeadCircumferenceCm(),
                        null,
                        row.getMeasurementMode()));

        row.setAgeDays(assessment.ageDays());
        row.setCorrectedAgeDays(assessment.correctedAgeDays());
        row.setCorrectedAgeApplied(assessment.correctedAgeApplied());
        row.setBmi(assessment.bmi());
        row.setWeightForAgeZ(assessment.zScore(GrowthIndicator.WEIGHT_FOR_AGE));
        row.setLengthHeightForAgeZ(assessment.zScore(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE));
        row.setBmiForAgeZ(assessment.zScore(GrowthIndicator.BODY_MASS_INDEX_FOR_AGE));
        row.setHeadCircumferenceForAgeZ(assessment.zScore(GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE));
        row.setPostmenstrualAgeWeeks(assessment.postmenstrualAgeWeeks());
        row.setGrowthStandard(assessment.standard());
        row.setGrowthEngineVersion(assessment.engineVersion());
        row.setScoringNote(assessment.unsupportedReason());
        row.setScoringGaps(serialiseGaps(assessment.unavailableIndicators()));

        NutritionClassifier.NutritionAssessment nutrition = NutritionClassifier.classify(
                assessment.ageDays(),
                row.getMuacCm(),
                oedemaOf(row.getOedema()),
                row.getWeightForAgeZ());
        row.setNutritionStatus(nutrition.status().name());
        row.setNutritionContentVersion(nutrition.contentVersion());
    }

    /**
     * Gestational age at birth from this child's own birth record. A failure to read it is
     * logged and treated as unknown: the measurement still gets recorded, and the row says
     * the gestational age was not established rather than implying the baby was term.
     */
    private Integer gestationalAgeFromBirthRecord(String subjectCpid) {
        try {
            return newbornRepository
                    .findByTenantIdAndSubjectCpid(TrustContextHolder.require().tenantId(), subjectCpid)
                    .map(NewbornBirthRecordEntity::getGestationalAgeWeeks)
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("Growth scoring could not read the birth record for gestational age: {}", e.getMessage());
            return null;
        }
    }

    private String serialiseGaps(Map<GrowthIndicator, String> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return null;
        }
        Map<String, String> byKey = new LinkedHashMap<>();
        gaps.forEach((indicator, reason) -> byKey.put(indicator.datasetKey(), reason));
        try {
            return objectMapper.writeValueAsString(byKey);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise growth scoring gaps: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> resolveDemographics(String subjectCpid) {
        try {
            Map<String, Object> demographics = vitoIntegration.resolvePatientByCpid(subjectCpid);
            return demographics == null || demographics.isEmpty() ? null : demographics;
        } catch (RuntimeException e) {
            log.warn("Growth scoring could not resolve demographics for subject: {}", e.getMessage());
            return null;
        }
    }

    private void emit(GrowthMeasurementEntity row) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("growthId", row.getGrowthId().toString());
        payload.put("subjectCpid", row.getSubjectCpid());
        payload.put("encounterId", row.getEncounterId());
        payload.put("measuredAt", row.getMeasuredAt().toString());
        payload.put("ageDays", row.getAgeDays());
        payload.put("weightForAgeZ", row.getWeightForAgeZ());
        payload.put("nutritionStatus", row.getNutritionStatus());
        payload.put("growthStandard", row.getGrowthStandard());
        payload.put("actorId", ctx.actorId());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("GROWTH_MEASUREMENT");
        outbox.setAggregateId(row.getGrowthId().toString());
        outbox.setEventType("pct.growth.recorded");
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialise growth event payload: {}", e.getMessage());
            return "{}";
        }
    }

    private static NutritionClassifier.Oedema oedemaOf(String value) {
        if (value == null) {
            return NutritionClassifier.Oedema.NOT_ASSESSED;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PRESENT", "YES", "TRUE" -> NutritionClassifier.Oedema.PRESENT;
            case "ABSENT", "NO", "FALSE" -> NutritionClassifier.Oedema.ABSENT;
            default -> NutritionClassifier.Oedema.NOT_ASSESSED;
        };
    }

    private static OffsetDateTime parseTimestamp(String value) {
        if (value == null) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("measured_at is not a valid timestamp: " + value);
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
            throw new IllegalArgumentException("Not a valid measurement value: " + text);
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
