package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;

import java.util.*;

/**
 * Communication Noticeboard — announcements, clinical pages, and messages.
 * Delegates to channels-service / notification-service via CommunityServiceClient.
 */
@RestController
@RequestMapping("/internal/v1/communication")
public class CommunicationController {

    private final CommunityServiceClient communityClient;

    public CommunicationController(CommunityServiceClient communityClient) {
        this.communityClient = communityClient;
    }

    // ── Announcements ───────────────────────────────────────────────

    @GetMapping("/announcements")
    public ResponseEntity<Map<String, Object>> listAnnouncements(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        JsonNode result = communityClient.listUnits();

        // List<Map<String, Object>> rows = jdbc.queryForList(sql, ...);

        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of()));
    }

    @GetMapping("/announcements/{id}")
    public ResponseEntity<Map<String, Object>> getAnnouncement(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        JsonNode result = communityClient.getUnit(id.toString());

        // List<Map<String, Object>> rows = jdbc.queryForList(..., id, tenantId);

        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", result));
    }

    @PostMapping("/announcements")
    public ResponseEntity<Map<String, Object>> createAnnouncement(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {

        body.put("tenantId", tenantId);
        JsonNode result = communityClient.createUnit(body);

        // jdbc.update("""
        //     INSERT INTO announcements (...) VALUES (...)
        //     """, ...);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result));
    }

    @PostMapping("/announcements/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAnnouncement(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        communityClient.startVisit(id.toString());

        // jdbc.update("INSERT INTO announcement_acknowledgments ...", ...);

        return ResponseEntity.ok(Map.of("acknowledged", true));
    }

    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<Map<String, Object>> archiveAnnouncement(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        communityClient.completeVisit(id.toString(), Map.of("status", "ARCHIVED"));

        // jdbc.update("UPDATE announcements SET status = 'ARCHIVED' ...", id, tenantId);

        return ResponseEntity.ok(Map.of("archived", true));
    }

    // ── Clinical Pages ──────────────────────────────────────────────

    @GetMapping("/pages")
    public ResponseEntity<Map<String, Object>> listPages(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String recipientId) {

        JsonNode result = communityClient.listVisits(recipientId);

        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of()));
    }

    @PostMapping("/pages")
    public ResponseEntity<Map<String, Object>> sendPage(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {

        body.put("tenantId", tenantId);
        JsonNode result = communityClient.createVisit(body);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result));
    }

    @PostMapping("/pages/{id}/read")
    public ResponseEntity<Map<String, Object>> markPageRead(@PathVariable UUID id) {
        communityClient.startVisit(id.toString());
        return ResponseEntity.ok(Map.of("status", "READ"));
    }

    @PostMapping("/pages/{id}/respond")
    public ResponseEntity<Map<String, Object>> respondToPage(@PathVariable UUID id) {
        communityClient.completeVisit(id.toString(), Map.of("status", "RESPONDED"));
        return ResponseEntity.ok(Map.of("status", "RESPONDED"));
    }

    // ── Clinical Messages ───────────────────────────────────────────

    @GetMapping("/messages/channels")
    public ResponseEntity<Map<String, Object>> listChannels(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        JsonNode result = communityClient.listUnits();
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of()));
    }

    @GetMapping("/messages/channels/{channelId}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            @PathVariable UUID channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        JsonNode result = communityClient.listAssignments(channelId.toString());
        return ResponseEntity.ok(Map.of("data", result != null ? result : List.of()));
    }

    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        body.put("tenantId", tenantId);
        JsonNode result = communityClient.createAssignment(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result));
    }
}
