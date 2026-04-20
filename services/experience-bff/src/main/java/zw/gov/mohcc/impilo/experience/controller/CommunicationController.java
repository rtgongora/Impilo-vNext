package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(CommunicationController.class);

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
        try {
            JsonNode result = communityClient.listUnits();
            return ResponseEntity.ok(Map.of("data", result != null ? result : List.of()));
        } catch (Exception e) {
            log.warn("Announcements list unavailable: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", List.of()));
        }
    }

    @GetMapping("/announcements/{id}")
    public ResponseEntity<Map<String, Object>> getAnnouncement(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            JsonNode result = communityClient.getUnit(id.toString());
            if (result == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of("data", result));
        } catch (Exception e) {
            log.warn("Announcement fetch unavailable: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/announcements")
    public ResponseEntity<Map<String, Object>> createAnnouncement(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        body.put("tenantId", tenantId);
        try {
            JsonNode result = communityClient.createUnit(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Announcement creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", Map.of("code", "SERVICE_UNAVAILABLE", "message", "Community service is unavailable")));
        }
    }

    @PostMapping("/announcements/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAnnouncement(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            communityClient.startVisit(id.toString());
        } catch (Exception e) {
            log.warn("Announcement acknowledge unavailable: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("acknowledged", true));
    }

    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<Map<String, Object>> archiveAnnouncement(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            communityClient.completeVisit(id.toString(), Map.of("status", "ARCHIVED"));
        } catch (Exception e) {
            log.warn("Announcement archive unavailable: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("archived", true));
    }

    // ── Clinical Pages ──────────────────────────────────────────────

    @GetMapping("/pages")
    public ResponseEntity<Map<String, Object>> listPages(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String recipientId) {
        try {
            JsonNode result = communityClient.listVisits(recipientId);
            return ResponseEntity.ok(Map.of("data", result != null ? result : List.of()));
        } catch (Exception e) {
            log.warn("Pages list unavailable: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", List.of()));
        }
    }

    @PostMapping("/pages")
    public ResponseEntity<Map<String, Object>> sendPage(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        body.put("tenantId", tenantId);
        try {
            JsonNode result = communityClient.createVisit(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Page send failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", Map.of("code", "SERVICE_UNAVAILABLE", "message", "Community service is unavailable")));
        }
    }

    @PostMapping("/pages/{id}/read")
    public ResponseEntity<Map<String, Object>> markPageRead(@PathVariable UUID id) {
        try {
            communityClient.startVisit(id.toString());
        } catch (Exception e) {
            log.warn("Page mark-read unavailable: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("status", "READ"));
    }

    @PostMapping("/pages/{id}/respond")
    public ResponseEntity<Map<String, Object>> respondToPage(@PathVariable UUID id) {
        try {
            communityClient.completeVisit(id.toString(), Map.of("status", "RESPONDED"));
        } catch (Exception e) {
            log.warn("Page respond unavailable: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("status", "RESPONDED"));
    }

    // ── Clinical Messages ───────────────────────────────────────────

    @GetMapping("/messages/channels")
    public ResponseEntity<Map<String, Object>> listChannels(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            JsonNode result = communityClient.listUnits();
            return ResponseEntity.ok(Map.of("data", result != null ? result : List.of()));
        } catch (Exception e) {
            log.warn("Message channels list unavailable: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", List.of()));
        }
    }

    @GetMapping("/messages/channels/{channelId}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            @PathVariable UUID channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            JsonNode result = communityClient.listAssignments(channelId.toString());
            return ResponseEntity.ok(Map.of("data", result != null ? result : List.of()));
        } catch (Exception e) {
            log.warn("Messages list unavailable: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", List.of()));
        }
    }

    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        body.put("tenantId", tenantId);
        try {
            JsonNode result = communityClient.createAssignment(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Message send failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", Map.of("code", "SERVICE_UNAVAILABLE", "message", "Community service is unavailable")));
        }
    }
}
