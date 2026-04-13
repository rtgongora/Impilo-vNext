package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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

    private final CommunityServiceClient communityClient;

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

        JsonNode groups = communityClient.listGroups(category, page, limit);

        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ...);

        return ResponseEntity.ok(Map.of("data", groups != null ? groups : List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/groups")
    @Transactional
    public ResponseEntity<Map<String, Object>> createGroup(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        body.put("tenantId", tenantId);
        JsonNode result = communityClient.createGroup(body);

        // jdbcTemplate.update("INSERT INTO community_groups (...) VALUES (...)", ...);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", result,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/groups/{groupId}/join")
    @Transactional
    public ResponseEntity<Map<String, Object>> joinGroup(
            @PathVariable UUID groupId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        JsonNode result = communityClient.joinGroup(groupId.toString(), body);

        // jdbcTemplate.update("INSERT INTO community_group_members (...) VALUES (...)", ...);

        return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of("joined", true),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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

        JsonNode posts = communityClient.listPosts(groupId.toString(), page, limit);

        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., groupId, tenantId, limit, offset);

        return ResponseEntity.ok(Map.of("data", posts != null ? posts : List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/groups/{groupId}/posts")
    @Transactional
    public ResponseEntity<Map<String, Object>> createPost(
            @PathVariable UUID groupId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        body.put("tenantId", tenantId);
        JsonNode result = communityClient.createPost(groupId.toString(), body);

        // jdbcTemplate.update("INSERT INTO discussion_posts (...) VALUES (...)", ...);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", result,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
