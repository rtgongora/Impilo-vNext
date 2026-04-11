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
 * Health Connect–equivalent ingest surface for Impilo citizen wellness.
 * <p>Android Health Connect: <a href="https://developer.android.com/health-and-fitness/guides/health-connect">Health Connect</a>.
 * This API accepts a changeset of typed records, dedupes by {@code records[].id}, and maps into
 * {@code wellness_activities} / {@code wellness_vitals_log}.</p>
 */
@RestController
@RequestMapping("/internal/v1/wellness/connect/v1")
public class WellnessHealthConnectController {

    private static final Logger log = LoggerFactory.getLogger(WellnessHealthConnectController.class);

    private final WellnessHealthConnectIngestService ingestService;

    public WellnessHealthConnectController(WellnessHealthConnectIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /** Supported record types and scope hints (for client capability negotiation). */
    @GetMapping("/manifest")
    public ResponseEntity<Map<String, Object>> manifest() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api", "impilo.wellness.connect.v1");
        body.put("alignment", "Inspired by Android Health Connect record types; not a licensed Google API.");
        body.put("supportedRecordTypes", List.of("Steps", "Hydration", "SleepSession", "HeartRate"));
        body.put("suggestedScopes", List.of(
                "read.steps", "write.steps",
                "read.hydration", "write.hydration",
                "read.sleep", "write.sleep",
                "read.heart_rate", "write.heart_rate"));
        body.put("dedupeKey", "records[].id scoped per patientId + tenant");
        body.put("aggregationModel", Map.of(
                "steps", "sum per local calendar day (UTC boundary); JSON field count",
                "hydration", "sum ml per startTime local day; JSON field volumeLiters (liters)",
                "sleep", "last session wins per wake-day (endTime or startTime); hoursSlept optional if endTime set",
                "heartRate", "append samples to wellness_vitals_log; beatsPerMinute or samples[{time,beatsPerMinute}]"));
        return ResponseEntity.ok(Map.of("data", body));
    }

    @PostMapping("/changesets")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody HealthConnectChangeSetRequest body) {
        log.debug("HC ingest patientId={} records={} platform={}", body.patientId(), body.records().size(), body.dataOrigin().platform());
        WellnessHealthConnectIngestService.IngestResult r = ingestService.ingest(tenantId, body);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applied", r.applied());
        data.put("skipped", r.skipped());
        data.put("errors", r.errors());
        return ResponseEntity.ok(Map.of("data", data));
    }
}
