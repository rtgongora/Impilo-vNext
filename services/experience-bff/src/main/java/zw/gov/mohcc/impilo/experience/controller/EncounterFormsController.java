package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Patient encounter structured-forms experience API — proxies the canonical PCT resolver + response
 * lifecycle. PCT owns the resolution decision, the version-locked response, extraction and audit; the BFF
 * composes only (data + meta envelope, trust headers forwarded by ServiceClientConfig).
 */
@RestController
@RequestMapping("/internal/v1")
public class EncounterFormsController {

    private static final Logger log = LoggerFactory.getLogger(EncounterFormsController.class);

    private final PctServiceClient pctClient;

    public EncounterFormsController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    /** Resolve the patient/cadre/setting-aware form set for an encounter. */
    @PostMapping("/encounters/{encounterId}/forms/resolve")
    public ResponseEntity<Map<String, Object>> resolve(
            @PathVariable String encounterId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> req = body == null ? new HashMap<>() : new HashMap<>(body);
        req.putIfAbsent("encounterId", parseEncounterId(encounterId));
        return proxy(() -> pctClient.resolveEncounterForms(req), requestId, correlationId,
                Map.of("encounter_id", encounterId));
    }

    /** Create a version-locked DRAFT response for a form on an encounter. */
    @PostMapping("/encounters/{encounterId}/forms/{formKey}/responses/draft")
    public ResponseEntity<Map<String, Object>> createDraft(
            @PathVariable String encounterId,
            @PathVariable String formKey,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> req = new HashMap<>();
        req.put("encounterId", parseEncounterId(encounterId));
        req.put("formKey", formKey);
        req.put("answers", body == null ? Map.of() : body.getOrDefault("answers", Map.of()));
        return proxy(() -> pctClient.createFormResponse(req), requestId, correlationId,
                Map.of("encounter_id", encounterId));
    }

    @GetMapping("/form-responses/{responseId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String responseId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.getFormResponse(responseId), requestId, correlationId, Map.of());
    }

    @PutMapping("/form-responses/{responseId}")
    public ResponseEntity<Map<String, Object>> updateAnswers(
            @PathVariable String responseId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.updateFormAnswers(responseId, body), requestId, correlationId, Map.of());
    }

    @PostMapping("/form-responses/{responseId}/submit")
    public ResponseEntity<Map<String, Object>> submit(
            @PathVariable String responseId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.submitFormResponse(responseId, body == null ? Map.of() : body),
                requestId, correlationId, Map.of());
    }

    @PostMapping("/form-responses/{responseId}/countersign")
    public ResponseEntity<Map<String, Object>> countersign(
            @PathVariable String responseId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.countersignFormResponse(responseId, body == null ? Map.of() : body),
                requestId, correlationId, Map.of());
    }

    @PostMapping("/form-responses/{responseId}/amend")
    public ResponseEntity<Map<String, Object>> amend(
            @PathVariable String responseId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.amendFormResponse(responseId, body), requestId, correlationId, Map.of());
    }

    @PostMapping("/form-responses/{responseId}/void")
    public ResponseEntity<Map<String, Object>> voidResponse(
            @PathVariable String responseId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.voidFormResponse(responseId, body), requestId, correlationId, Map.of());
    }

    @GetMapping("/encounters/{encounterId}/form-responses")
    public ResponseEntity<Map<String, Object>> listForEncounter(
            @PathVariable String encounterId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.listEncounterFormResponses(encounterId), requestId, correlationId,
                Map.of("encounter_id", encounterId));
    }

    // ------------------------------------------------------------------

    private interface Call {
        JsonNode invoke();
    }

    private ResponseEntity<Map<String, Object>> proxy(Call call, String requestId, String correlationId,
                                                      Map<String, Object> extraMeta) {
        try {
            JsonNode data = call.invoke();
            if (data == null) {
                return upstreamFailure(requestId, correlationId, "No payload returned");
            }
            Map<String, Object> meta = new HashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            meta.putAll(extraMeta);
            Map<String, Object> out = new HashMap<>();
            out.put("data", data);
            out.put("meta", meta);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.warn("PCT encounter-forms proxy failed: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(String requestId, String correlationId, String msg) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", "PCT_UNAVAILABLE", "message", msg == null ? "upstream error" : msg),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private Object parseEncounterId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
