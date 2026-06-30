package zw.gov.mohcc.impilo.experience.wellness;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.SimbaServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wellness home composition (Simba wellness DEPTH). Aggregates the citizen's wellness state from
 * the Simba sovereign (profile, goals, plans + enrolments, today's habits, coaching, open
 * care-linkages) into one resilient overview, and orchestrates wellness-to-care routing across
 * Simba (records the linkage truth) and PCT (the clinical referral owner).
 *
 * <p>Stateless composition only — no datasource. Simba owns the wellness data; PCT owns the
 * referral lifecycle and result. The CRUD endpoints themselves are reverse-proxied to Simba by
 * {@code WellnessServiceProxyController}; this controller only adds the composite + cross-service
 * orchestration the proxy cannot do.</p>
 */
@RestController
@RequestMapping("/internal/v1/wellness/home")
public class WellnessHomeController {

    private static final Logger log = LoggerFactory.getLogger(WellnessHomeController.class);

    private final SimbaServiceClient simba;
    private final PctServiceClient pct;

    public WellnessHomeController(SimbaServiceClient simba, PctServiceClient pct) {
        this.simba = simba;
        this.pct = pct;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @RequestHeader(value = CompanionHeaders.SUBJECT_ID, required = false) String subjectId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        String cpid = firstNonBlank(subjectId, actorId);
        if (cpid == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "NO_PERSON_ANCHOR",
                            "message", "X-Subject-ID or X-Actor-ID is required")));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("person_cpid", cpid);
        // Each section degrades independently so one slow/failed downstream never blanks the home.
        data.put("profile", safe(() -> simba.getWellnessProfile(cpid), "profile"));
        data.put("goals", safe(() -> simba.listGoals(cpid), "goals"));
        data.put("plans", safe(simba::listPlans, "plans"));
        data.put("enrollments", safe(() -> simba.listEnrollments(cpid), "enrollments"));
        data.put("today_habits", safe(() -> simba.listHabitCheckIns(cpid), "today_habits"));
        data.put("coaching", safe(() -> simba.listCoaching(cpid), "coaching"));
        data.put("care_linkages", safe(() -> simba.listCareLinkages(cpid), "care_linkages"));
        data.put("clubs", safe(simba::listClubs, "clubs"));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return ResponseEntity.ok(Map.of("data", data, "meta", meta));
    }

    /**
     * Wellness-to-care routing. Records a care linkage in Simba (wellness truth + event) and, for
     * a clinical (non-emergency) trigger, also creates a PCT referral so the clinical lifecycle is
     * owned by PCT. Emergency severity is routed to the emergency entry, not a routine referral.
     */
    @PostMapping("/care-routing")
    public ResponseEntity<Map<String, Object>> routeCare(
            @RequestHeader(value = CompanionHeaders.SUBJECT_ID, required = false) String subjectId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestBody Map<String, Object> body) {
        String cpid = firstNonBlank(
                body.get("person_cpid") == null ? null : body.get("person_cpid").toString(),
                firstNonBlank(subjectId, actorId));
        if (cpid == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "NO_PERSON_ANCHOR",
                            "message", "person_cpid (or X-Subject-ID/X-Actor-ID) is required")));
        }
        String severity = body.get("severity") == null ? "ROUTINE" : body.get("severity").toString();
        String trigger = body.get("trigger") == null ? "RISK_THRESHOLD" : body.get("trigger").toString();

        Map<String, Object> linkageReq = new LinkedHashMap<>();
        linkageReq.put("person_cpid", cpid);
        linkageReq.put("trigger", trigger);
        linkageReq.put("severity", severity);
        if (body.get("reason") != null) linkageReq.put("reason", body.get("reason"));

        // 1) Record the wellness linkage truth in Simba (always).
        JsonNode linkage;
        try {
            linkage = simba.routeCare(linkageReq);
        } catch (Exception e) {
            log.error("Wellness care linkage failed for cpid={}: {}", cpid, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "CARE_LINKAGE_FAILED", "message", e.getMessage())));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("linkage", linkage);

        // 2) For a non-emergency clinical trigger, create a PCT referral (clinical owner).
        if (!"EMERGENCY".equalsIgnoreCase(severity)) {
            Map<String, Object> referralReq = new LinkedHashMap<>();
            referralReq.put("patientCpid", cpid);
            referralReq.put("referralSource", "WELLNESS");
            referralReq.put("reason", body.getOrDefault("reason", trigger));
            try {
                out.put("referral", pct.createReferral(referralReq));
                out.put("referral_status", "ROUTED");
            } catch (Exception e) {
                // Partial success: the linkage is recorded; surface the referral failure honestly.
                log.warn("PCT referral from wellness routing failed for cpid={}: {}", cpid, e.getMessage());
                out.put("referral_status", "REFERRAL_PENDING");
                out.put("referral_error", e.getMessage());
            }
        } else {
            out.put("referral_status", "EMERGENCY_ROUTED");
            out.put("emergency_guidance",
                    "This appears urgent. Use emergency services or your nearest emergency entry now.");
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", out, "meta", meta));
    }

    private interface DownstreamCall {
        JsonNode call();
    }

    private JsonNode safe(DownstreamCall c, String section) {
        try {
            return c.call();
        } catch (Exception e) {
            log.warn("Wellness home section '{}' unavailable: {}", section, e.getMessage());
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
