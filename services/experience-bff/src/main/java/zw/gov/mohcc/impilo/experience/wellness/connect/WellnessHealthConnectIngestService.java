package zw.gov.mohcc.impilo.experience.wellness.connect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Maps Health Connect–style records into {@code wellness_activities}, {@code wellness_vitals_log},
 * {@code wellness_sleep_segments}, {@code wellness_exercise_sessions}, and
 * {@code wellness_connect_extension} for long-tail / unmapped HC datatypes (full JSON payload).
 */
@Service
public class WellnessHealthConnectIngestService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTx;

    public WellnessHealthConnectIngestService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = new TransactionTemplate(transactionManager, def);
    }

    public record IngestResult(int applied, int skipped, List<String> errors) {}

    public IngestResult ingest(String tenantId, HealthConnectChangeSetRequest request) {
        int applied = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<HealthConnectChangeSetRequest.HealthRecord> records =
                request.records() == null ? List.of() : request.records();

        for (HealthConnectChangeSetRequest.HealthRecord record : records) {
            try {
                Boolean didApply = requiresNewTx.execute(status -> {
                    String payloadJson = serializeRecord(record);
                    if (insertDedupeRow(tenantId, request.patientId(), record, request.dataOrigin(), payloadJson) == 0) {
                        return false;
                    }
                    applyRecordInternal(tenantId, request.patientId(), record);
                    return true;
                });
                if (Boolean.TRUE.equals(didApply)) {
                    applied++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                errors.add(record.id() + ": " + e.getMessage());
            }
        }

        return new IngestResult(applied, skipped, errors);
    }

    private String serializeRecord(HealthConnectChangeSetRequest.HealthRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private void applyRecordInternal(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        String c = canonicalRecordType(record.type());
        switch (c) {
            case "STEPS" -> mergeSteps(tenantId, patientId, record);
            case "WHEELCHAIR_PUSHES" -> mergeWheelchairPushes(tenantId, patientId, record);
            case "HYDRATION" -> mergeHydration(tenantId, patientId, record);
            case "SLEEPSESSION" -> mergeSleep(tenantId, patientId, record);
            case "HEARTRATE" -> insertHeartRate(tenantId, patientId, record);
            case "DISTANCE" -> mergeDistance(tenantId, patientId, record);
            case "FLOORS_CLIMBED" -> insertFloors(tenantId, patientId, record);
            case "ACTIVE_ENERGY" -> mergeActiveEnergy(tenantId, patientId, record);
            case "TOTAL_ENERGY" -> mergeTotalEnergy(tenantId, patientId, record);
            case "WEIGHT" -> insertScalarVital(tenantId, patientId, record, "WEIGHT_KG", weight(record), "kg", record.startTime());
            case "HEIGHT" -> insertScalarVital(tenantId, patientId, record, "HEIGHT_CM", height(record), "cm", record.startTime());
            case "BODY_FAT" -> insertScalarVital(tenantId, patientId, record, "BODY_FAT_PERCENT", bodyFat(record), "percent", record.startTime());
            case "BLOOD_PRESSURE" -> insertBloodPressure(tenantId, patientId, record);
            case "BLOOD_GLUCOSE" -> insertScalarVital(tenantId, patientId, record, "BLOOD_GLUCOSE_MG_DL", record.bloodGlucoseMgDl(), "mg/dL", record.startTime());
            case "OXYGEN_SATURATION" -> insertScalarVital(tenantId, patientId, record, "OXYGEN_SATURATION", record.oxygenSaturationPercent(), "percent", record.startTime());
            case "RESTING_HEART_RATE" -> insertScalarVital(tenantId, patientId, record, "RESTING_HEART_RATE", restingHr(record), "bpm", record.startTime());
            case "HRV_RMSSD" -> insertScalarVital(tenantId, patientId, record, "HRV_RMSSD_MS", hrv(record), "ms", record.startTime());
            case "NUTRITION" -> mergeNutritionFull(tenantId, patientId, record);
            case "EXERCISE_SESSION" -> upsertExerciseSession(tenantId, patientId, record);
            case "ELEVATION_GAINED" -> insertScalarVital(
                    tenantId, patientId, record, "ELEVATION_GAINED_M", record.elevationGainedMeters(), "m", record.startTime());
            case "ACTIVE_MINUTES" -> mergeActiveMinutes(tenantId, patientId, record);
            case "STEPS_CADENCE" -> insertScalarVital(
                    tenantId, patientId, record, "STEPS_CADENCE_RPM", record.stepsCadenceRpm(), "rpm", record.startTime());
            case "CYCLING_PEDALING_CADENCE" -> insertScalarVital(
                    tenantId, patientId, record, "CYCLING_PEDALING_CADENCE_RPM", record.cyclingPedalingCadenceRpm(), "rpm", record.startTime());
            case "POWER" -> insertScalarVital(tenantId, patientId, record, "POWER_W", record.powerWatts(), "W", record.startTime());
            case "SPEED" -> insertScalarVital(tenantId, patientId, record, "SPEED_MS", record.speedMetersPerSecond(), "m/s", record.startTime());
            case "RESPIRATORY_RATE" -> insertScalarVital(
                    tenantId, patientId, record, "RESPIRATORY_RATE", record.respiratoryRateBpm(), "breaths/min", record.startTime());
            case "BODY_TEMPERATURE" -> insertScalarVital(
                    tenantId, patientId, record, "BODY_TEMPERATURE_C", record.bodyTemperatureCelsius(), "C", record.startTime());
            case "BASAL_METABOLIC_RATE" -> insertScalarVital(
                    tenantId, patientId, record, "BASAL_METABOLIC_RATE_KCAL", record.basalMetabolicRateKcal(), "kcal/day", record.startTime());
            case "LEAN_BODY_MASS" -> insertScalarVital(tenantId, patientId, record, "LEAN_BODY_MASS_KG", record.leanBodyMassKg(), "kg", record.startTime());
            case "BONE_MASS" -> insertScalarVital(tenantId, patientId, record, "BONE_MASS_KG", record.boneMassKg(), "kg", record.startTime());
            case "VO2_MAX" -> insertScalarVital(tenantId, patientId, record, "VO2_MAX", record.vo2MaxMlKgMin(), "mL/kg/min", record.startTime());
            case "BODY_WATER_MASS" -> insertScalarVital(tenantId, patientId, record, "BODY_WATER_MASS_KG", record.bodyWaterMassKg(), "kg", record.startTime());
            case "MINDFULNESS_SESSION" -> mergeMindfulnessMinutes(tenantId, patientId, record);
            default -> upsertExtension(tenantId, patientId, record);
        }
    }

    /** HC datatype names with optional {@code Record} suffix and common aliases → canonical token. */
    public static String canonicalRecordType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String t = raw.trim();
        if (t.endsWith("Record")) {
            t = t.substring(0, t.length() - 6);
        }
        t = t.replace('-', '_').replace(' ', '_');
        String u = t.toUpperCase(Locale.ROOT);
        return switch (u) {
            case "HEART_RATE", "HEARTRATE" -> "HEARTRATE";
            case "RESTING_HEART_RATE", "RESTINGHEARTRATE" -> "RESTING_HEART_RATE";
            case "SLEEP_SESSION", "SLEEPSESSION" -> "SLEEPSESSION";
            case "HYDRATION" -> "HYDRATION";
            case "ACTIVE_CALORIES_BURNED", "ACTIVECALORIESBURNED", "ACTIVE_ENERGY_BURNED", "ACTIVEENERGYBURNED" -> "ACTIVE_ENERGY";
            case "TOTAL_CALORIES_BURNED", "TOTALCALORIESBURNED", "TOTAL_ENERGY_BURNED" -> "TOTAL_ENERGY";
            case "FLOORS_CLIMBED", "FLOORSCLIMBED" -> "FLOORS_CLIMBED";
            case "BLOOD_PRESSURE", "BLOODPRESSURE" -> "BLOOD_PRESSURE";
            case "BLOOD_GLUCOSE", "BLOODGLUCOSE" -> "BLOOD_GLUCOSE";
            case "OXYGEN_SATURATION", "OXYGENSATURATION" -> "OXYGEN_SATURATION";
            case "HEART_RATE_VARIABILITY_RMSSD", "HEARTRATEVARIABILITYRMSSD", "HEARTRATEVARIABILITY_RMSSD", "HRV_RMSSD" -> "HRV_RMSSD";
            case "NUTRITION", "NUTRITIONRECORD" -> "NUTRITION";
            case "EXERCISE_SESSION", "EXERCISESESSION" -> "EXERCISE_SESSION";
            case "WHEELCHAIRPUSHES", "WHEELCHAIR_PUSHES" -> "WHEELCHAIR_PUSHES";
            case "ELEVATIONGAINED",
                    "ELEVATION_GAINED",
                    "ELEVATIONGAINEDDURINGEXERCISE",
                    "ELEVATION_GAINED_DURING_EXERCISE",
                    "ELEVATIONGAINED_DURING_EXERCISE",
                    "ELEVATION_GAINED_DURINGEXERCISE" -> "ELEVATION_GAINED";
            case "ACTIVE_MINUTES", "ACTIVE_MINUTES_BURNED", "ACTIVEMINUTESBURNED" -> "ACTIVE_MINUTES";
            case "STEPS_CADENCE", "STEP_CADENCE" -> "STEPS_CADENCE";
            case "CYCLING_PEDALING_CADENCE", "CYCLINGPEDALINGCADENCE" -> "CYCLING_PEDALING_CADENCE";
            case "POWER" -> "POWER";
            case "SPEED" -> "SPEED";
            case "RESPIRATORY_RATE", "RESPIRATORYRATE" -> "RESPIRATORY_RATE";
            case "BODY_TEMPERATURE", "BODYTEMPERATURE", "BASAL_BODY_TEMPERATURE", "BASALBODYTEMPERATURE" -> "BODY_TEMPERATURE";
            case "BASAL_METABOLIC_RATE", "BASALMETABOLICRATE" -> "BASAL_METABOLIC_RATE";
            case "LEAN_BODY_MASS", "LEANBODYMASS" -> "LEAN_BODY_MASS";
            case "BONE_MASS", "BONEMASS" -> "BONE_MASS";
            case "VO2_MAX", "VO2MAX" -> "VO2_MAX";
            case "BODY_WATER_MASS", "BODYWATERMASS" -> "BODY_WATER_MASS";
            case "MINDFULNESS_SESSION", "MINDFULNESSSESSION" -> "MINDFULNESS_SESSION";
            case "MENSTRUATIONFLOW", "MENSTRUATION_FLOW" -> "MENSTRUATION_FLOW";
            default -> u;
        };
    }

    private int insertDedupeRow(
            String tenantId,
            String patientId,
            HealthConnectChangeSetRequest.HealthRecord record,
            HealthConnectChangeSetRequest.DataOrigin origin,
            String payloadJson) {
        return jdbc.update(
                """
                INSERT INTO wellness_connect_ingest_log (id, tenant_id, patient_id, external_record_id, record_type, data_origin_platform, data_origin_package, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (tenant_id, patient_id, external_record_id) DO NOTHING
                """,
                UUID.randomUUID(),
                tenantId,
                patientId,
                record.id(),
                canonicalRecordType(record.type()),
                origin.platform(),
                origin.appPackage() != null ? origin.appPackage() : "",
                payloadJson != null && !payloadJson.isBlank() ? payloadJson : "{}");
    }

    private void mergeSteps(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.count() == null || record.count() < 0) {
            throw new IllegalArgumentException("Steps.count required and must be >= 0");
        }
        addStepsDelta(tenantId, patientId, activityLocalDate(record), record.count().intValue());
    }

    private void mergeWheelchairPushes(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.wheelchairPushes() == null || record.wheelchairPushes() < 0) {
            throw new IllegalArgumentException("WheelchairPushes requires wheelchairPushes count >= 0");
        }
        addStepsDelta(tenantId, patientId, activityLocalDate(record), record.wheelchairPushes().intValue());
    }

    private void addStepsDelta(String tenantId, String patientId, LocalDate day, int delta) {
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, ?, ?, 0, 0, 0, NULL, NULL, 0)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    steps = wellness_activities.steps + EXCLUDED.steps
                """,
                tenantId, patientId, day, delta);
    }

    private void mergeHydration(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        BigDecimal vol = record.volumeLiters();
        if (vol == null || vol.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Hydration requires volume (liters) as volumeLiters or volume alias");
        }
        int ml = vol.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).intValue();
        LocalDate day = activityLocalDate(record);
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, ?, 0, 0, 0, 0, NULL, NULL, ?)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    water_ml = wellness_activities.water_ml + EXCLUDED.water_ml
                """,
                tenantId, patientId, day, ml);
    }

    private void mergeSleep(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        OffsetDateTime end = record.endTime() != null && !record.endTime().isBlank()
                ? parseOffset(record.endTime())
                : parseOffset(record.startTime());
        LocalDate wakeDay = end.toLocalDate();
        BigDecimal hours = record.hoursSlept();
        if (hours == null) {
            OffsetDateTime start = parseOffset(record.startTime());
            hours = BigDecimal.valueOf(java.time.Duration.between(start, end).toMinutes() / 60.0)
                    .setScale(1, RoundingMode.HALF_UP);
        }
        if (hours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Sleep session must have positive duration");
        }
        String quality = record.sleepQuality() != null ? String.valueOf(record.sleepQuality()) : null;
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, ?, 0, 0, 0, 0, ?, ?, 0)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    sleep_hours = EXCLUDED.sleep_hours,
                    sleep_quality = COALESCE(EXCLUDED.sleep_quality, wellness_activities.sleep_quality)
                """,
                tenantId, patientId, wakeDay, hours, quality);

        if (record.sleepStages() != null && !record.sleepStages().isEmpty()) {
            jdbc.update(
                    "DELETE FROM wellness_sleep_segments WHERE tenant_id = ? AND patient_id = ? AND session_external_id = ?",
                    tenantId, patientId, record.id());
            for (HealthConnectChangeSetRequest.HealthRecord.SleepStageEntry st : record.sleepStages()) {
                jdbc.update(
                        """
                        INSERT INTO wellness_sleep_segments (id, tenant_id, patient_id, session_external_id, stage, start_at, end_at)
                        VALUES (?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz)
                        """,
                        UUID.randomUUID(),
                        tenantId,
                        patientId,
                        record.id(),
                        normalizeStage(st.stage()),
                        parseOffset(st.startTime()),
                        parseOffset(st.endTime()));
            }
        }
    }

    private static String normalizeStage(String s) {
        if (s == null) return "UNKNOWN";
        return s.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private void insertHeartRate(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.samples() != null && !record.samples().isEmpty()) {
            int cap = Math.min(record.samples().size(), 500);
            for (int i = 0; i < cap; i++) {
                var s = record.samples().get(i);
                insertOneVital(tenantId, patientId, parseOffset(s.time()), "HEART_RATE", BigDecimal.valueOf(s.beatsPerMinute()), "bpm", null);
            }
        } else if (record.beatsPerMinute() != null && record.beatsPerMinute() > 0) {
            insertOneVital(tenantId, patientId, parseOffset(record.startTime()), "HEART_RATE", BigDecimal.valueOf(record.beatsPerMinute()), "bpm", null);
        } else {
            throw new IllegalArgumentException("HeartRate requires beatsPerMinute or samples[]");
        }
    }

    private void mergeDistance(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.distanceMeters() == null || record.distanceMeters().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Distance requires distanceMeters (or alias distance)");
        }
        BigDecimal deltaKm = record.distanceMeters().divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        LocalDate day = activityLocalDate(record);
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, ?, 0, 0, 0, ?, NULL, NULL, 0)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    distance_km = wellness_activities.distance_km + EXCLUDED.distance_km
                """,
                tenantId, patientId, day, deltaKm);
    }

    private void insertFloors(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.floorsClimbed() == null || record.floorsClimbed() < 0) {
            throw new IllegalArgumentException("FloorsClimbed requires floorsClimbed (or alias floors)");
        }
        insertOneVital(tenantId, patientId, parseOffset(record.startTime()), "FLOORS", BigDecimal.valueOf(record.floorsClimbed()), "floors", null);
    }

    private void mergeActiveEnergy(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        BigDecimal kcal = record.activeEnergyKcal();
        if (kcal == null || kcal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Active energy requires activeEnergyKcal");
        }
        int delta = kcal.setScale(0, RoundingMode.HALF_UP).intValue();
        LocalDate day = activityLocalDate(record);
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, ?, 0, ?, 0, 0, NULL, NULL, 0)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    calories_burned = wellness_activities.calories_burned + EXCLUDED.calories_burned
                """,
                tenantId, patientId, day, delta);
    }

    private void mergeTotalEnergy(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        BigDecimal kcal = record.totalEnergyKcal();
        if (kcal == null || kcal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total energy requires totalEnergyKcal");
        }
        int delta = kcal.setScale(0, RoundingMode.HALF_UP).intValue();
        LocalDate day = activityLocalDate(record);
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, ?, 0, ?, 0, 0, NULL, NULL, 0)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    calories_burned = wellness_activities.calories_burned + EXCLUDED.calories_burned
                """,
                tenantId, patientId, day, delta);
    }

    private void mergeNutritionEnergy(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        BigDecimal kcal = record.nutritionEnergyKcal();
        if (kcal == null || kcal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Nutrition requires nutritionEnergyKcal (or alias energy)");
        }
        int delta = kcal.setScale(0, RoundingMode.HALF_UP).intValue();
        LocalDate day = activityLocalDate(record);
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, ?, 0, ?, 0, 0, NULL, NULL, 0)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    calories_burned = wellness_activities.calories_burned + EXCLUDED.calories_burned
                """,
                tenantId, patientId, day, delta);
    }

    private void mergeNutritionFull(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.nutritionEnergyKcal() != null && record.nutritionEnergyKcal().compareTo(BigDecimal.ZERO) >= 0) {
            mergeNutritionEnergy(tenantId, patientId, record);
        }
        mergeNutritionMacros(tenantId, patientId, record);
    }

    private void mergeNutritionMacros(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        OffsetDateTime t = parseOffset(record.startTime());
        if (record.nutritionProteinG() != null && record.nutritionProteinG().compareTo(BigDecimal.ZERO) >= 0) {
            insertOneVital(tenantId, patientId, t, "NUTRITION_PROTEIN_G", record.nutritionProteinG(), "g", null);
        }
        if (record.nutritionCarbsG() != null && record.nutritionCarbsG().compareTo(BigDecimal.ZERO) >= 0) {
            insertOneVital(tenantId, patientId, t, "NUTRITION_CARBS_G", record.nutritionCarbsG(), "g", null);
        }
        if (record.nutritionFatG() != null && record.nutritionFatG().compareTo(BigDecimal.ZERO) >= 0) {
            insertOneVital(tenantId, patientId, t, "NUTRITION_FAT_G", record.nutritionFatG(), "g", null);
        }
        if ((record.nutritionEnergyKcal() == null || record.nutritionEnergyKcal().compareTo(BigDecimal.ZERO) < 0)
                && record.nutritionProteinG() == null
                && record.nutritionCarbsG() == null
                && record.nutritionFatG() == null) {
            throw new IllegalArgumentException("Nutrition requires nutritionEnergyKcal and/or macro grams");
        }
    }

    private void mergeActiveMinutes(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.activeMinutes() == null || record.activeMinutes() < 0) {
            throw new IllegalArgumentException("Active minutes requires activeMinutes >= 0");
        }
        LocalDate day = activityLocalDate(record);
        int m = record.activeMinutes();
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, ?, 0, 0, ?, 0, NULL, NULL, 0)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    active_minutes = wellness_activities.active_minutes + EXCLUDED.active_minutes
                """,
                tenantId, patientId, day, m);
    }

    private void mergeMindfulnessMinutes(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.mindfulnessSessionMinutes() == null || record.mindfulnessSessionMinutes().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("MindfulnessSession requires mindfulnessSessionMinutes >= 0");
        }
        int m = record.mindfulnessSessionMinutes().setScale(0, RoundingMode.HALF_UP).intValue();
        LocalDate day = activityLocalDate(record);
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, ?, 0, 0, ?, 0, NULL, NULL, 0)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    active_minutes = wellness_activities.active_minutes + EXCLUDED.active_minutes
                """,
                tenantId, patientId, day, m);
    }

    private void upsertExtension(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        String c = canonicalRecordType(record.type());
        String payload = serializeRecord(record);
        OffsetDateTime start = tryParseOffset(record.startTime());
        OffsetDateTime end = tryParseOffset(record.endTime());
        jdbc.update(
                """
                INSERT INTO wellness_connect_extension (id, tenant_id, patient_id, external_record_id, canonical_type, start_at, end_at, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (tenant_id, patient_id, external_record_id) DO UPDATE SET
                    canonical_type = EXCLUDED.canonical_type,
                    start_at = EXCLUDED.start_at,
                    end_at = EXCLUDED.end_at,
                    payload = EXCLUDED.payload
                """,
                UUID.randomUUID(),
                tenantId,
                patientId,
                record.id(),
                c,
                start,
                end,
                payload != null && !payload.isBlank() ? payload : "{}");
    }

    private static OffsetDateTime tryParseOffset(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return parseOffset(iso);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Local calendar day for daily rollups: uses {@code startZoneOffset} with zone-less {@code startTime},
     * or converts an instant {@code startTime} into the wall date for that offset when offset is present.
     */
    private LocalDate activityLocalDate(HealthConnectChangeSetRequest.HealthRecord record) {
        String zo = record.startZoneOffset();
        if (zo != null && !zo.isBlank()) {
            try {
                ZoneOffset offset = ZoneOffset.of(zo.trim());
                String s = record.startTime();
                if (s != null && (s.endsWith("Z") || hasTimeZoneSuffix(s))) {
                    return parseOffset(s).withOffsetSameInstant(offset).toLocalDate();
                }
                LocalDateTime ldt = LocalDateTime.parse(trimFractionalSeconds(s));
                return ldt.atOffset(offset).toLocalDate();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return parseOffset(record.startTime()).toLocalDate();
    }

    private static boolean hasTimeZoneSuffix(String iso) {
        if (iso.length() < 6) {
            return false;
        }
        int t = iso.indexOf('T');
        if (t < 0) {
            return false;
        }
        for (int i = iso.length() - 1; i > t; i--) {
            char c = iso.charAt(i);
            if (c == 'Z') {
                return true;
            }
            if ((c == '+' || c == '-') && i > t + 2) {
                return true;
            }
        }
        return false;
    }

    private static String trimFractionalSeconds(String iso) {
        int dot = iso.indexOf('.');
        if (dot < 0) {
            return iso;
        }
        int t = iso.indexOf('T');
        if (dot <= t) {
            return iso;
        }
        int i = dot + 1;
        while (i < iso.length() && Character.isDigit(iso.charAt(i))) {
            i++;
        }
        return iso.substring(0, dot) + iso.substring(i);
    }

    private void insertBloodPressure(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.bloodPressureSystolic() == null || record.bloodPressureDiastolic() == null) {
            throw new IllegalArgumentException("BloodPressure requires bloodPressureSystolic and bloodPressureDiastolic");
        }
        OffsetDateTime t = parseOffset(record.startTime());
        insertOneVital(tenantId, patientId, t, "BLOOD_PRESSURE_SYSTOLIC", BigDecimal.valueOf(record.bloodPressureSystolic()), "mmHg", null);
        insertOneVital(tenantId, patientId, t, "BLOOD_PRESSURE_DIASTOLIC", BigDecimal.valueOf(record.bloodPressureDiastolic()), "mmHg", null);
    }

    private void insertScalarVital(
            String tenantId,
            String patientId,
            HealthConnectChangeSetRequest.HealthRecord record,
            String vitalType,
            BigDecimal value,
            String unit,
            String timeIso) {
        if (value == null) {
            throw new IllegalArgumentException(vitalType + " requires a numeric value");
        }
        insertOneVital(tenantId, patientId, parseOffset(timeIso != null ? timeIso : record.startTime()), vitalType, value, unit, null);
    }

    private static BigDecimal weight(HealthConnectChangeSetRequest.HealthRecord r) {
        return r.weightKg();
    }

    private static BigDecimal height(HealthConnectChangeSetRequest.HealthRecord r) {
        return r.heightCm();
    }

    private static BigDecimal bodyFat(HealthConnectChangeSetRequest.HealthRecord r) {
        return r.bodyFatPercent();
    }

    private static BigDecimal restingHr(HealthConnectChangeSetRequest.HealthRecord r) {
        return r.restingHeartRateBpm() != null ? BigDecimal.valueOf(r.restingHeartRateBpm()) : null;
    }

    private static BigDecimal hrv(HealthConnectChangeSetRequest.HealthRecord r) {
        return r.hrvRmssdMilliseconds() != null ? BigDecimal.valueOf(r.hrvRmssdMilliseconds()) : null;
    }

    private void upsertExerciseSession(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.endTime() == null || record.endTime().isBlank()) {
            throw new IllegalArgumentException("ExerciseSession requires endTime");
        }
        OffsetDateTime start = parseOffset(record.startTime());
        OffsetDateTime end = parseOffset(record.endTime());
        BigDecimal energy = record.exerciseEnergyKcal() != null ? record.exerciseEnergyKcal() : record.activeEnergyKcal();
        BigDecimal distM = record.exerciseDistanceMeters() != null ? record.exerciseDistanceMeters() : record.distanceMeters();
        jdbc.update(
                """
                INSERT INTO wellness_exercise_sessions (id, tenant_id, patient_id, external_record_id, exercise_type, title, start_at, end_at, energy_kcal, distance_m)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, patient_id, external_record_id) DO UPDATE SET
                    exercise_type = EXCLUDED.exercise_type,
                    title = EXCLUDED.title,
                    start_at = EXCLUDED.start_at,
                    end_at = EXCLUDED.end_at,
                    energy_kcal = EXCLUDED.energy_kcal,
                    distance_m = EXCLUDED.distance_m
                """,
                UUID.randomUUID(),
                tenantId,
                patientId,
                record.id(),
                record.exerciseType(),
                record.exerciseTitle(),
                start,
                end,
                energy,
                distM);
    }

    private void insertOneVital(
            String tenantId,
            String patientId,
            OffsetDateTime measuredAt,
            String vitalType,
            BigDecimal value,
            String unit,
            String notes) {
        jdbc.update(
                """
                INSERT INTO wellness_vitals_log (id, tenant_id, patient_id, vital_type, value, unit, measured_at, source, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'HEALTH_CONNECT', ?)
                """,
                UUID.randomUUID(),
                tenantId,
                patientId,
                vitalType,
                value,
                unit,
                measuredAt,
                notes);
    }

    private static OffsetDateTime parseOffset(String iso) {
        if (iso == null || iso.isBlank()) {
            throw new IllegalArgumentException("time required");
        }
        try {
            return OffsetDateTime.parse(iso);
        } catch (DateTimeParseException e) {
            try {
                return java.time.ZonedDateTime.parse(iso).toOffsetDateTime();
            } catch (DateTimeParseException e2) {
                return java.time.LocalDateTime.parse(iso).atOffset(java.time.ZoneOffset.UTC);
            }
        }
    }
}
