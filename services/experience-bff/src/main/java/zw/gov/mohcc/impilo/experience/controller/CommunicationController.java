package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
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
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        try {
            JsonNode result = communityClient.listUnits();
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Announcements list unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/announcements/{id}")
    public ResponseEntity<Map<String, Object>> getAnnouncement(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = communityClient.getUnit(id.toString());
            if (result == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "ANNOUNCEMENT_NOT_FOUND", "message", "Announcement not found"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return ResponseEntity.ok(Map.of(
                    "data", result,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Announcement fetch unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/announcements")
    public ResponseEntity<Map<String, Object>> createAnnouncement(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        body.put("tenantId", tenantId);
        try {
            JsonNode result = communityClient.createUnit(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Announcement creation failed: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/announcements/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAnnouncement(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            communityClient.startVisit(id.toString());
            return ResponseEntity.ok(Map.of(
                    "acknowledged", true,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Announcement acknowledge unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<Map<String, Object>> archiveAnnouncement(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            communityClient.completeVisit(id.toString(), Map.of("status", "ARCHIVED"));
            return ResponseEntity.ok(Map.of(
                    "archived", true,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Announcement archive unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    // ── Clinical Pages ──────────────────────────────────────────────

    @GetMapping("/pages")
    public ResponseEntity<Map<String, Object>> listPages(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String recipientId) {
        try {
            JsonNode result = communityClient.listVisits(recipientId);
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Pages list unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/pages")
    public ResponseEntity<Map<String, Object>> sendPage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        body.put("tenantId", tenantId);
        try {
            JsonNode result = communityClient.createVisit(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Page send failed: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/pages/{id}/read")
    public ResponseEntity<Map<String, Object>> markPageRead(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            communityClient.startVisit(id.toString());
            return ResponseEntity.ok(Map.of(
                    "status", "READ",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Page mark-read unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/pages/{id}/respond")
    public ResponseEntity<Map<String, Object>> respondToPage(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            communityClient.completeVisit(id.toString(), Map.of("status", "RESPONDED"));
            return ResponseEntity.ok(Map.of(
                    "status", "RESPONDED",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Page respond unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    // ── Clinical Messages ───────────────────────────────────────────

    @GetMapping("/messages/channels")
    public ResponseEntity<Map<String, Object>> listChannels(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = communityClient.listUnits();
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Message channels list unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/messages/channels/{channelId}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            @PathVariable UUID channelId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            JsonNode result = communityClient.listAssignments(channelId.toString());
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Messages list unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        body.put("tenantId", tenantId);
        try {
            JsonNode result = communityClient.createAssignment(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Message send failed: {}", e.getMessage());
            return upstreamFailure("COMMUNITY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Communication upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
