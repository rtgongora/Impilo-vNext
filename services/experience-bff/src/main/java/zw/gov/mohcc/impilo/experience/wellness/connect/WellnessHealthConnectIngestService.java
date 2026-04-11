package zw.gov.mohcc.impilo.experience.wellness.connect;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Maps Health Connect–style {@link HealthConnectChangeSetRequest} records into
 * {@code wellness_activities} (daily rollup) and {@code wellness_vitals_log} (heart rate samples).
 * Each record runs in {@code REQUIRES_NEW} so a failed mapping rolls back its dedupe insert.
 */
@Service
public class WellnessHealthConnectIngestService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate requiresNewTx;

    public WellnessHealthConnectIngestService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
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
                    if (insertDedupeRow(tenantId, request.patientId(), record, request.dataOrigin()) == 0) {
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

    private void applyRecordInternal(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        String t = normalizeType(record.type());
        switch (t) {
            case "STEPS" -> mergeSteps(tenantId, patientId, record);
            case "HYDRATION" -> mergeHydration(tenantId, patientId, record);
            case "SLEEPSESSION", "SLEEP_SESSION" -> mergeSleep(tenantId, patientId, record);
            case "HEARTRATE", "HEART_RATE" -> insertHeartRate(tenantId, patientId, record);
            default -> throw new IllegalArgumentException("Unsupported record type: " + record.type());
        }
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    /** @return 1 if new ingest, 0 if duplicate external id */
    private int insertDedupeRow(
            String tenantId,
            String patientId,
            HealthConnectChangeSetRequest.HealthRecord record,
            HealthConnectChangeSetRequest.DataOrigin origin) {
        return jdbc.update(
                """
                INSERT INTO wellness_connect_ingest_log (id, tenant_id, patient_id, external_record_id, record_type, data_origin_platform, data_origin_package)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, patient_id, external_record_id) DO NOTHING
                """,
                UUID.randomUUID(),
                tenantId,
                patientId,
                record.id(),
                normalizeType(record.type()),
                origin.platform(),
                origin.appPackage() != null ? origin.appPackage() : "");
    }

    private void mergeSteps(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.count() == null || record.count() < 0) {
            throw new IllegalArgumentException("Steps.count required and must be >= 0");
        }
        LocalDate day = parseLocalDate(record.startTime());
        int delta = record.count().intValue();
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, ?, ?, 0, 0, 0, NULL, NULL, 0)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    steps = wellness_activities.steps + EXCLUDED.steps
                """,
                tenantId,
                patientId,
                day,
                delta);
    }

    private void mergeHydration(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.volumeLiters() == null || record.volumeLiters().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Hydration.volumeLiters required and must be >= 0");
        }
        int ml = record.volumeLiters().multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).intValue();
        LocalDate day = parseLocalDate(record.startTime());
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, ?, 0, 0, 0, 0, NULL, NULL, ?)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    water_ml = wellness_activities.water_ml + EXCLUDED.water_ml
                """,
                tenantId,
                patientId,
                day,
                ml);
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
                tenantId,
                patientId,
                wakeDay,
                hours,
                quality);
    }

    private void insertHeartRate(String tenantId, String patientId, HealthConnectChangeSetRequest.HealthRecord record) {
        if (record.samples() != null && !record.samples().isEmpty()) {
            int cap = Math.min(record.samples().size(), 500);
            for (int i = 0; i < cap; i++) {
                var s = record.samples().get(i);
                OffsetDateTime t = parseOffset(s.time());
                insertOneVital(tenantId, patientId, t, BigDecimal.valueOf(s.beatsPerMinute()));
            }
        } else if (record.beatsPerMinute() != null && record.beatsPerMinute() > 0) {
            insertOneVital(tenantId, patientId, parseOffset(record.startTime()), BigDecimal.valueOf(record.beatsPerMinute()));
        } else {
            throw new IllegalArgumentException("HeartRate requires beatsPerMinute or samples[]");
        }
    }

    private void insertOneVital(String tenantId, String patientId, OffsetDateTime measuredAt, BigDecimal bpm) {
        jdbc.update(
                """
                INSERT INTO wellness_vitals_log (id, tenant_id, patient_id, vital_type, value, unit, measured_at, source, notes)
                VALUES (?, ?, ?, 'HEART_RATE', ?, 'bpm', ?, 'HEALTH_CONNECT', NULL)
                """,
                UUID.randomUUID(),
                tenantId,
                patientId,
                bpm,
                measuredAt);
    }

    private static LocalDate parseLocalDate(String iso) {
        return parseOffset(iso).toLocalDate();
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
