package zw.gov.mohcc.impilo.experience.wellness.connect;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Health Connect parity surface — typed changesets, idempotent ingest, read-back for sleep stages
 * and exercise sessions.
 */
@RestController
@RequestMapping("/internal/v1/wellness/connect/v1")
public class WellnessHealthConnectController {

    private static final Logger log = LoggerFactory.getLogger(WellnessHealthConnectController.class);

    private final WellnessHealthConnectIngestService ingestService;
    private final WellnessHealthConnectQueryService queryService;

    public WellnessHealthConnectController(
            WellnessHealthConnectIngestService ingestService,
            WellnessHealthConnectQueryService queryService) {
        this.ingestService = ingestService;
        this.queryService = queryService;
    }

    /** Capability matrix for Android Health Connect–style clients. */
    @GetMapping("/manifest")
    public ResponseEntity<Map<String, Object>> manifest() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api", "impilo.wellness.connect.v1");
        body.put("alignment", "Parity with Android Health Connect datatype semantics; not a licensed Google API.");
        body.put("supportedRecordTypes", List.of(
                "Steps", "WheelchairPushes", "Distance", "FloorsClimbed",
                "ActiveCaloriesBurned", "TotalCaloriesBurned",
                "Hydration", "Nutrition",
                "SleepSession", "HeartRate", "RestingHeartRate", "HeartRateVariabilityRmssd",
                "Weight", "Height", "BodyFat",
                "BloodPressure", "BloodGlucose", "OxygenSaturation",
                "ExerciseSession",
                "ElevationGained", "ActiveMinutesBurned",
                "StepsCadence", "CyclingPedalingCadence", "Power", "Speed",
                "RespiratoryRate", "BodyTemperature", "BasalMetabolicRate",
                "LeanBodyMass", "BoneMass", "Vo2Max", "BodyWaterMass",
                "MindfulnessSession"));
        body.put(
                "extensionPassthrough",
                "Any other Health Connect datatype is accepted on ingest and stored in wellness_connect_extension (dedupe key + full JSON payload).");
        body.put("typeAliases", Map.of(
                "description", "Incoming type may include Record suffix (e.g. StepsRecord) or omit it.",
                "canonicalization", "See WellnessHealthConnectIngestService.canonicalRecordType"));
        body.put("storageMapping", List.of(
                Map.of("types", List.of("Steps"), "store", "wellness_activities.steps", "aggregate", "sum by activity_date"),
                Map.of("types", List.of("Distance"), "store", "wellness_activities.distance_km", "aggregate", "sum km from distanceMeters"),
                Map.of("types", List.of("Hydration"), "store", "wellness_activities.water_ml", "aggregate", "sum ml from volume (liters)"),
                Map.of("types", List.of("ActiveCaloriesBurned", "TotalCaloriesBurned", "Nutrition"), "store", "wellness_activities.calories_burned", "aggregate", "sum kcal"),
                Map.of("types", List.of("SleepSession"), "store", "wellness_activities.sleep_hours + sleep_quality", "extra", "wellness_sleep_segments from sleepStages[]"),
                Map.of("types", List.of("HeartRate", "RestingHeartRate", "BloodPressure", "BloodGlucose", "OxygenSaturation", "FloorsClimbed", "HeartRateVariabilityRmssd", "Weight", "Height", "BodyFat"),
                        "store", "wellness_vitals_log", "aggregate", "append row per sample or per scalar reading"),
                Map.of("types", List.of("ExerciseSession"), "store", "wellness_exercise_sessions", "aggregate", "upsert by external_record_id"),
                Map.of("types", List.of("WheelchairPushes"), "store", "wellness_activities.steps", "aggregate", "sum pushes into steps column (HC parity)"),
                Map.of("types", List.of("ElevationGained"), "store", "wellness_vitals_log", "aggregate", "append ELEVATION_GAINED_M"),
                Map.of("types", List.of("ActiveMinutesBurned", "MindfulnessSession"),
                        "store", "wellness_activities.active_minutes", "aggregate", "sum minutes by activity_date"),
                Map.of("types", List.of("Other HC types (e.g. MenstruationFlow, OvulationTest, PlannedExerciseSession)"),
                        "store", "wellness_connect_extension", "aggregate", "upsert JSON payload by external_record_id")));
        body.put("suggestedScopes", List.of(
                "read.steps", "write.steps",
                "read.distance", "write.distance",
                "read.floors_climbed", "write.floors_climbed",
                "read.active_calories_burned", "write.active_calories_burned",
                "read.total_calories_burned", "write.total_calories_burned",
                "read.hydration", "write.hydration",
                "read.nutrition", "write.nutrition",
                "read.sleep", "write.sleep",
                "read.heart_rate", "write.heart_rate",
                "read.resting_heart_rate", "write.resting_heart_rate",
                "read.oxygen_saturation", "write.oxygen_saturation",
                "read.blood_pressure", "write.blood_pressure",
                "read.blood_glucose", "write.blood_glucose",
                "read.weight", "write.weight",
                "read.height", "write.height",
                "read.body_fat", "write.body_fat",
                "read.exercise", "write.exercise"));
        body.put("dedupeKey", "records[].id scoped per patientId + tenant (wellness_connect_ingest_log)");
        body.put("payloadEcho", "Full JSON record stored in wellness_connect_ingest_log.payload for audit/replay.");
        body.put("readEndpoints", List.of(
                "GET /sleep-segments?patientId=&limit=",
                "GET /exercise-sessions?patientId=&days=",
                "GET /ingest-log?patientId=&limit=&includePayload=",
                "GET /extension-records?patientId=&limit="));
        return ResponseEntity.ok(Map.of("data", body));
    }

    @GetMapping("/sleep-segments")
    public ResponseEntity<Map<String, Object>> sleepSegments(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> rows = queryService.sleepSegments(tenantId, patientId, limit);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @GetMapping("/exercise-sessions")
    public ResponseEntity<Map<String, Object>> exerciseSessions(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> rows = queryService.exerciseSessions(tenantId, patientId, days);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @GetMapping("/ingest-log")
    public ResponseEntity<Map<String, Object>> ingestLog(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean includePayload) {
        List<Map<String, Object>> rows = queryService.ingestLog(tenantId, patientId, limit, includePayload);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @GetMapping("/extension-records")
    public ResponseEntity<Map<String, Object>> extensionRecords(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> rows = queryService.extensionRecords(tenantId, patientId, limit);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/changesets")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody HealthConnectChangeSetRequest body) {
        int n = body.records() == null ? 0 : body.records().size();
        log.debug("HC ingest patientId={} records={} platform={}", body.patientId(), n, body.dataOrigin().platform());
        WellnessHealthConnectIngestService.IngestResult r = ingestService.ingest(tenantId, body);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applied", r.applied());
        data.put("skipped", r.skipped());
        data.put("errors", r.errors());
        return ResponseEntity.ok(Map.of("data", data));
    }
}
