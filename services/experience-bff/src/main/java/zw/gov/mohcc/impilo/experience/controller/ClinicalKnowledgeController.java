package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BFF for national clinical knowledge (EDLIZ-aligned). Proxies to clinical-knowledge-platform-service.
 * Canonical route prefix is {@code /internal/v1/clinical}; {@code /internal/v1/clinical-knowledge}
 * is a temporary backward-compatibility alias.
 */
@RestController
@RequestMapping({"/internal/v1/clinical", "/internal/v1/clinical-knowledge"})
public class ClinicalKnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalKnowledgeController.class);

    private final ClinicalKnowledgePlatformClient clinicalClient;

    public ClinicalKnowledgeController(ClinicalKnowledgePlatformClient clinicalClient) {
        this.clinicalClient = clinicalClient;
    }

    @PostMapping("/assistant/ask")
    public ResponseEntity<Map<String, Object>> askEdliz(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.assistantAsk(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            // Category (b): defensible fallback. Unlike the alert paths below, this payload names
            // its own unavailability — answer_summary says the service is down and support_mode
            // is INSUFFICIENT_EVIDENCE — so the clinician cannot mistake it for a real "no
            // recommendation" answer. The 200 carries an honest body rather than a silent one.
            log.error("Clinical assistant ask failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", fallbackUnsupported()));
        }
    }

    @GetMapping("/assistant/traces/{id}")
    public ResponseEntity<Map<String, Object>> trace(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        try {
            JsonNode data = clinicalClient.getTrace(id);
            if (data == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of("data", data));
        } catch (Exception e) {
            log.error("Clinical trace fetch failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/prescribing/evaluate")
    public ResponseEntity<Map<String, Object>> prescribing(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.SKIP_PRESCRIBING_TRACE, required = false) String skipTrace,
            @RequestHeader(value = CompanionHeaders.PRESCRIBING_RECORD_TRACE, required = false) String tracePolicy,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> merged = mergePrescribingEvaluateBody(skipTrace, tracePolicy, body);
            JsonNode data = clinicalClient.prescribingEvaluate(merged);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            // Zero alerts is not the neutral answer here — it is the safety claim the prescriber
            // acts on ("checked, nothing contraindicated"). Returning it when the check never ran
            // clears the drug on the strength of an outage.
            log.error("Clinical prescribing evaluate failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "prescribing_check_unavailable",
                    "message", "Prescribing safety checks could not be run. Do not treat this as "
                               + "an absence of alerts."));
        }
    }

    @PostMapping("/rules/evaluate")
    public ResponseEntity<Map<String, Object>> rulesEvaluate(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.rulesEvaluate(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of("alerts", java.util.List.of())));
        } catch (Exception e) {
            // An empty alert list is indistinguishable from a rules run that found nothing, so
            // this reported "no alerts" whenever the engine was unreachable.
            log.error("Clinical rules evaluate failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "rules_evaluation_unavailable",
                    "message", "Clinical rules could not be evaluated. Do not treat this as an "
                               + "absence of alerts."));
        }
    }

    @PostMapping("/interpretation/evaluate")
    public ResponseEntity<Map<String, Object>> interpretationEvaluate(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.interpretationEvaluate(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : emptyInterpretation()));
        } catch (Exception e) {
            // An empty interpretation reads as "these results were reviewed and nothing was
            // flagged", which is the opposite of what happened.
            log.error("Clinical interpretation evaluate failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "interpretation_unavailable",
                    "message", "Results could not be interpreted. Do not treat this as an absence "
                               + "of abnormal findings."));
        }
    }

    private static Map<String, Object> emptyInterpretation() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("interpreted_observations", java.util.List.of());
        m.put("alerts", java.util.List.of());
        m.put("ranges_used", java.util.List.of());
        return m;
    }

    @PostMapping("/cds/summary")
    public ResponseEntity<Map<String, Object>> cdsSummary(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.cdsSummary(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : java.util.Map.of("insight", "")));
        } catch (Exception e) {
            // A null insight is the same payload a successful "nothing to add" run produces, so
            // the caller cannot tell a quiet engine from an absent one.
            log.error("Clinical CDS summary failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "cds_summary_unavailable",
                    "message", "Decision support could not be reached. Do not treat this as an "
                               + "absence of guidance."));
        }
    }

    @PostMapping("/audit/overrides")
    public ResponseEntity<Map<String, Object>> recordOverride(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.recordOverride(body, actorId);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Clinical override record failed: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "override_not_recorded"));
        }
    }

    @GetMapping("/pathways")
    public ResponseEntity<Map<String, Object>> pathways(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        try {
            JsonNode data = clinicalClient.listPathways();
            return ResponseEntity.ok(Map.of("data", data != null ? data : java.util.List.of()));
        } catch (Exception e) {
            // An empty catalogue reads as "this facility has no care pathways", which would
            // quietly remove protocol guidance from the clinician's options.
            log.error("Clinical pathways list failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "pathways_unavailable",
                    "message", "Care pathways could not be retrieved. Do not treat this as an "
                               + "absence of pathways."));
        }
    }

    @PostMapping("/pathways/sessions")
    public ResponseEntity<Map<String, Object>> startPathwaySession(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.startPathwaySession(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            // A 200 carrying {"error":"unavailable"} is a failed write dressed as a success; the
            // caller has no session but every reason to believe it started one.
            log.error("Clinical pathway session start failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "pathway_session_not_started",
                    "message", "The care pathway session could not be started."));
        }
    }

    @PostMapping("/pathways/sessions/{sessionId}/advance")
    public ResponseEntity<Map<String, Object>> advancePathwaySession(
            @PathVariable UUID sessionId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.advancePathwaySession(sessionId, body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            // As above: advancing a pathway is a write, and a 200 here claims a step was taken
            // that was not.
            log.error("Clinical pathway advance failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "pathway_advance_failed",
                    "message", "The care pathway step could not be recorded."));
        }
    }

    /**
     * Default {@code record_trace} is {@code true} for governed audit; headers override JSON body.
     * Skip wins over explicit trace policy for sandbox flows.
     */
    static Map<String, Object> mergePrescribingEvaluateBody(
            String skipTraceHeader, String tracePolicyHeader, Map<String, Object> body) {
        Map<String, Object> merged = new LinkedHashMap<>(body != null ? body : Map.of());
        boolean skip = "true".equalsIgnoreCase(trimOrEmpty(skipTraceHeader));
        if (skip) {
            merged.put("record_trace", false);
            return merged;
        }
        String policy = trimOrEmpty(tracePolicyHeader);
        if ("false".equalsIgnoreCase(policy)) {
            merged.put("record_trace", false);
        } else if ("true".equalsIgnoreCase(policy)) {
            merged.put("record_trace", true);
        } else if (!merged.containsKey("record_trace")) {
            merged.put("record_trace", true);
        }
        return merged;
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static Map<String, Object> fallbackUnsupported() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answer_summary", "The clinical knowledge service is temporarily unavailable. No automated recommendation was generated.");
        m.put("support_mode", "INSUFFICIENT_EVIDENCE");
        m.put("source_citations", java.util.List.of());
        m.put("warnings", java.util.List.of());
        return m;
    }
}
