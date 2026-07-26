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
 * Community groups and discussions. Delegates to CommunityServiceClient.
 *
 * GET  /internal/v1/community/groups — list groups
 * POST /internal/v1/community/groups — create group
 * GET  /internal/v1/community/groups/{id}/posts — list posts
 * POST /internal/v1/community/groups/{id}/posts — create post
 * POST /internal/v1/community/groups/{id}/join — join group
 */
@RestController
@RequestMapping("/internal/v1/community")
public class CommunityController {

    private static final Logger log = LoggerFactory.getLogger(CommunityController.class);

    private final CommunityServiceClient communityClient;

    public CommunityController(CommunityServiceClient communityClient) {
        this.communityClient = communityClient;
    }

    @GetMapping("/groups")
    public ResponseEntity<Map<String, Object>> listGroups(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        try {
            JsonNode groups = communityClient.listGroups(category, page, limit);
            return ResponseEntity.ok(Map.of("data", groups != null ? groups : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            // An empty 200 here is indistinguishable from a successful read that found
            // nothing, so a failed call was being reported as an absence.
            log.error("Community groups list failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "community_groups_unavailable",
                    "message", "Community groups could not be retrieved. Do not treat this as an absence of groups.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/groups")
    public ResponseEntity<Map<String, Object>> createGroup(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        body.put("tenantId", tenantId);
        try {
            JsonNode result = communityClient.createGroup(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Community group creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", Map.of("code", "SERVICE_UNAVAILABLE", "message", "Community service is unavailable")));
        }
    }

    @PostMapping("/groups/{groupId}/join")
    public ResponseEntity<Map<String, Object>> joinGroup(
            @PathVariable UUID groupId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        try {
            JsonNode result = communityClient.joinGroup(groupId.toString(), body);
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of("joined", true),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Community group join failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", Map.of("code", "SERVICE_UNAVAILABLE", "message", "Community service is unavailable")));
        }
    }

    @GetMapping("/groups/{groupId}/posts")
    public ResponseEntity<Map<String, Object>> listPosts(
            @PathVariable UUID groupId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        try {
            JsonNode posts = communityClient.listPosts(groupId.toString(), page, limit);
            return ResponseEntity.ok(Map.of("data", posts != null ? posts : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            // An empty 200 here is indistinguishable from a successful read that found
            // nothing, so a failed call was being reported as an absence.
            log.error("Community posts list failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "community_posts_unavailable",
                    "message", "Community posts could not be retrieved. Do not treat this as an absence of posts.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/groups/{groupId}/posts")
    public ResponseEntity<Map<String, Object>> createPost(
            @PathVariable UUID groupId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        body.put("tenantId", tenantId);
        try {
            JsonNode result = communityClient.createPost(groupId.toString(), body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Community post creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", Map.of("code", "SERVICE_UNAVAILABLE", "message", "Community service is unavailable")));
        }
    }
}
