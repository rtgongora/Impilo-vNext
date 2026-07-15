package zw.gov.mohcc.impilo.experience.wellness;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.SimbaServiceClient;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wellness provider workbench composition (Simba wellness completion). Composes a client wellness
 * summary (health-summary + assessments + follow-ups + care-linkages) for an approved wellness
 * provider, and offers write actions (record a measurement, create a follow-up/recommendation,
 * escalate to care) that the transparent proxy cannot compose.
 *
 * <p>Stateless composition only — Simba owns the wellness truth and enforces access via its
 * WellnessAccessGuard (provider + coaching-assignment + consent). Escalation records a Simba care
 * linkage; the clinical referral lifecycle stays with PCT (via WellnessHomeController.routeCare).</p>
 */
@RestController
@RequestMapping("/internal/v1/wellness/workbench")
public class WellnessWorkbenchController {

    private static final Logger log = LoggerFactory.getLogger(WellnessWorkbenchController.class);

    private final SimbaServiceClient simba;

    public WellnessWorkbenchController(SimbaServiceClient simba) {
        this.simba = simba;
    }

    @GetMapping("/client/{cpid}/summary")
    public ResponseEntity<Map<String, Object>> clientSummary(
            @PathVariable("cpid") String cpid,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("person_cpid", cpid);
        // Each section degrades independently so one slow/failed downstream never blanks the workbench.
        data.put("health_summary", safe(() -> simba.providerHealthSummary(cpid), "health_summary"));
        data.put("assessments", safe(() -> simba.listAssessments(cpid), "assessments"));
        data.put("follow_ups", safe(() -> simba.listFollowUpsForPerson(cpid), "follow_ups"));
        data.put("care_linkages", safe(() -> simba.listCareLinkages(cpid), "care_linkages"));
        data.put("coaching", safe(() -> simba.listCoaching(cpid), "coaching"));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return ResponseEntity.ok(Map.of("data", data, "meta", meta));
    }

    @GetMapping("/follow-ups")
    public ResponseEntity<Map<String, Object>> providerFollowUps(
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "NO_PROVIDER", "message", "X-Provider-ID is required")));
        }
        return ResponseEntity.ok(Map.of("data", safe(
                () -> simba.listFollowUpsForProvider(providerId), "provider_follow_ups")));
    }

    @PostMapping("/client/{cpid}/measurement")
    public ResponseEntity<Map<String, Object>> recordMeasurement(
            @PathVariable("cpid") String cpid,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> req = new HashMap<>(body);
        req.putIfAbsent("person_cpid", cpid);
        JsonNode result = simba.recordManualReading(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result));
    }

    @PostMapping("/client/{cpid}/follow-up")
    public ResponseEntity<Map<String, Object>> createFollowUp(
            @PathVariable("cpid") String cpid,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> req = new HashMap<>(body);
        req.putIfAbsent("person_cpid", cpid);
        JsonNode result = simba.createFollowUp(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result));
    }

    @PostMapping("/client/{cpid}/recommendation")
    public ResponseEntity<Map<String, Object>> recommend(
            @PathVariable("cpid") String cpid,
            @RequestBody Map<String, Object> body) {
        // A recommendation is modelled as an EDUCATE/REVIEW follow-up so it appears on the citizen timeline.
        Map<String, Object> req = new HashMap<>(body);
        req.putIfAbsent("person_cpid", cpid);
        req.putIfAbsent("origin_type", "TOUCHPOINT");
        req.putIfAbsent("action_type", "EDUCATE");
        JsonNode result = simba.createFollowUp(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result));
    }

    @PostMapping("/client/{cpid}/escalate")
    public ResponseEntity<Map<String, Object>> escalate(
            @PathVariable("cpid") String cpid,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> req = new HashMap<>(body);
        req.putIfAbsent("person_cpid", cpid);
        req.putIfAbsent("trigger", "COACH_ESCALATION");
        req.putIfAbsent("severity", "URGENT");
        req.putIfAbsent("target_owner", "PCT");
        JsonNode result = simba.routeCare(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result));
    }

    private interface DownstreamCall {
        JsonNode call();
    }

    private JsonNode safe(DownstreamCall c, String section) {
        try {
            return c.call();
        } catch (Exception e) {
            log.warn("Wellness workbench section '{}' unavailable: {}", section, e.getMessage());
            return null;
        }
    }
}
