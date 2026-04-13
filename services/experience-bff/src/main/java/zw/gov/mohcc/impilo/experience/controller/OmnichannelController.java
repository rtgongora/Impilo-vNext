package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;

import java.util.*;

/**
 * Omnichannel controller — callbacks, channel configs, disclosure rules,
 * SMS journeys, USSD menus, IVR flows. Delegates to channels-service via CommunityServiceClient.
 */
@RestController
@RequestMapping("/internal/v1/omnichannel")
public class OmnichannelController {

    private final CommunityServiceClient communityClient;

        this.communityClient = communityClient;
    }

    // ── Callbacks ────────────────────────────────────────────────

    @GetMapping("/callbacks")
    public ResponseEntity<Map<String, Object>> listCallbacks(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(required = false) String status) {

        JsonNode result = communityClient.listVisits(tenantId);

        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/callbacks")
    public ResponseEntity<Map<String, Object>> createCallback(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {

        Map<String, Object> callbackData = new LinkedHashMap<>(body);
        callbackData.put("tenantId", tenantId);
        JsonNode result = communityClient.createVisit(callbackData);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result, "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/callbacks/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeCallback(
            @PathVariable UUID id, @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody(required = false) Map<String, String> body) {

        Map<String, Object> completeData = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
        communityClient.completeVisit(id.toString(), completeData);

        return ResponseEntity.ok(Map.of("data", Map.of("id", id.toString(), "status", "COMPLETED"), "meta", Map.of("request_id", requestId)));
    }

    // ── Channel Configs ──────────────────────────────────────────

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> listChannels(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = communityClient.listUnits();
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    // ── SMS Journeys ─────────────────────────────────────────────

    @GetMapping("/sms-journeys")
    public ResponseEntity<Map<String, Object>> listSmsJourneys(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = communityClient.listUnits();
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/sms-journeys")
    public ResponseEntity<Map<String, Object>> createSmsJourney(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {
        Map<String, Object> journeyData = new LinkedHashMap<>(body);
        journeyData.put("tenantId", tenantId);
        JsonNode result = communityClient.createUnit(journeyData);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result, "meta", Map.of("request_id", requestId)));
    }

    // ── USSD Menus ───────────────────────────────────────────────

    @GetMapping("/ussd-menus")
    public ResponseEntity<Map<String, Object>> listUssdMenus(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = communityClient.listUnits();
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    // ── IVR Flows ────────────────────────────────────────────────

    @GetMapping("/ivr-flows")
    public ResponseEntity<Map<String, Object>> listIvrFlows(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = communityClient.listUnits();
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    // ── Disclosure Rules ─────────────────────────────────────────

    @GetMapping("/disclosure-rules")
    public ResponseEntity<Map<String, Object>> listDisclosureRules(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = communityClient.listUnits();
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/disclosure-rules")
    public ResponseEntity<Map<String, Object>> createDisclosureRule(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {
        Map<String, Object> ruleData = new LinkedHashMap<>(body);
        ruleData.put("tenantId", tenantId);
        JsonNode result = communityClient.createUnit(ruleData);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result, "meta", Map.of("request_id", requestId)));
    }
}
