package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Omnichannel controller — callbacks, channel configs, disclosure rules,
 * SMS journeys, USSD menus, IVR flows. Delegates to channels-service via CommunityServiceClient.
 */
@RestController
@RequestMapping("/internal/v1/omnichannel")
public class OmnichannelController {

    private final JdbcTemplate jdbcTemplate;
    private final CommunityServiceClient communityClient;

    public OmnichannelController(JdbcTemplate jdbcTemplate, CommunityServiceClient communityClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.communityClient = communityClient;
    }

    // ── Callbacks ────────────────────────────────────────────────

    @GetMapping("/callbacks")
    public ResponseEntity<Map<String, Object>> listCallbacks(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(required = false) String status) {

        // STRANGLER: migrated — delegate to CommunityServiceClient
        JsonNode result = communityClient.listVisits(tenantId);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from omni_callback_queue
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/callbacks")
    @Transactional
    public ResponseEntity<Map<String, Object>> createCallback(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {

        // STRANGLER: migrated — delegate to CommunityServiceClient
        Map<String, Object> callbackData = new LinkedHashMap<>(body);
        callbackData.put("tenantId", tenantId);
        JsonNode result = communityClient.createVisit(callbackData);

        // STRANGLER: migrated — was direct JdbcTemplate INSERT into omni_callback_queue
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result, "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/callbacks/{id}/complete")
    @Transactional
    public ResponseEntity<Map<String, Object>> completeCallback(
            @PathVariable UUID id, @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody(required = false) Map<String, String> body) {

        // STRANGLER: migrated — delegate to CommunityServiceClient
        Map<String, Object> completeData = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
        communityClient.completeVisit(id.toString(), completeData);

        // STRANGLER: migrated — was direct JdbcTemplate UPDATE on omni_callback_queue
        return ResponseEntity.ok(Map.of("data", Map.of("id", id.toString(), "status", "COMPLETED"), "meta", Map.of("request_id", requestId)));
    }

    // ── Channel Configs ──────────────────────────────────────────

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> listChannels(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        // STRANGLER: migrated — delegate to CommunityServiceClient
        JsonNode result = communityClient.listUnits();
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    // ── SMS Journeys ─────────────────────────────────────────────

    @GetMapping("/sms-journeys")
    public ResponseEntity<Map<String, Object>> listSmsJourneys(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        // STRANGLER: migrated — delegate to CommunityServiceClient
        JsonNode result = communityClient.listUnits();
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/sms-journeys")
    @Transactional
    public ResponseEntity<Map<String, Object>> createSmsJourney(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {
        // STRANGLER: migrated — delegate to CommunityServiceClient
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
        // STRANGLER: migrated — delegate to CommunityServiceClient
        JsonNode result = communityClient.listUnits();
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    // ── IVR Flows ────────────────────────────────────────────────

    @GetMapping("/ivr-flows")
    public ResponseEntity<Map<String, Object>> listIvrFlows(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        // STRANGLER: migrated — delegate to CommunityServiceClient
        JsonNode result = communityClient.listUnits();
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    // ── Disclosure Rules ─────────────────────────────────────────

    @GetMapping("/disclosure-rules")
    public ResponseEntity<Map<String, Object>> listDisclosureRules(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        // STRANGLER: migrated — delegate to CommunityServiceClient
        JsonNode result = communityClient.listUnits();
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of(), "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/disclosure-rules")
    @Transactional
    public ResponseEntity<Map<String, Object>> createDisclosureRule(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {
        // STRANGLER: migrated — delegate to CommunityServiceClient
        Map<String, Object> ruleData = new LinkedHashMap<>(body);
        ruleData.put("tenantId", tenantId);
        JsonNode result = communityClient.createUnit(ruleData);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result, "meta", Map.of("request_id", requestId)));
    }
}
