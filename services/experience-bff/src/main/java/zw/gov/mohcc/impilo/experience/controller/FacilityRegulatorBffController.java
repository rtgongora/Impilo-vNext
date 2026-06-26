package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Multi-regulator facility&lt;-&gt;council relationship composer (stateless). Surfaces the
 * WGV facility-regulator relationship SoR for facility-admin and regulator-mode UI.
 * No hardcoded single regulator. Authz via existing ext_authz; policies
 * {@code ORG-ADMIN}, {@code REGULATOR-MODE} (spec-only, track P).
 */
@RestController
@RequestMapping("/internal/v1/facility-mode")
public class FacilityRegulatorBffController {

    private static final Logger log = LoggerFactory.getLogger(FacilityRegulatorBffController.class);

    private final WorkforceGovernanceClient wgvClient;

    public FacilityRegulatorBffController(WorkforceGovernanceClient wgvClient) {
        this.wgvClient = wgvClient;
    }

    @GetMapping("/{facilityId}/regulators")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode body = wgvClient.getJson("/v1/internal/governance/facilities/" + facilityId + "/regulators");
            return ResponseEntity.ok(Map.of("data", unwrap(body), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "REGULATORS_UNAVAILABLE");
        }
    }

    @PostMapping("/{facilityId}/regulators")
    public ResponseEntity<Map<String, Object>> link(
            @PathVariable String facilityId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode resp = wgvClient.postJson(
                    "/v1/internal/governance/facilities/" + facilityId + "/regulators", body);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("data", unwrap(resp), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "REGULATOR_LINK_FAILED");
        }
    }

    @PatchMapping("/regulators/{relationshipId}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String relationshipId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode resp = wgvClient.patchJson(
                    "/v1/internal/governance/facilities/regulators/" + relationshipId + "/status", body);
            return ResponseEntity.ok(Map.of("data", unwrap(resp), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "REGULATOR_STATUS_FAILED");
        }
    }

    private static JsonNode unwrap(JsonNode body) {
        if (body != null && body.has("data")) return body.get("data");
        return body;
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return meta;
    }

    private ResponseEntity<Map<String, Object>> badGateway(String requestId, String correlationId, String code) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", "Workforce-governance upstream unavailable"),
                "meta", meta(requestId, correlationId)));
    }
}
