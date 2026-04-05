package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Community groups and discussions.
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

    private final JdbcTemplate jdbcTemplate;

    public CommunityController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        int offset = page * limit;

        String sql = category != null
            ? "SELECT * FROM community_groups WHERE tenant_id = ? AND status = 'ACTIVE' AND category = ? ORDER BY member_count DESC LIMIT ? OFFSET ?"
            : "SELECT * FROM community_groups WHERE tenant_id = ? AND status = 'ACTIVE' ORDER BY member_count DESC LIMIT ? OFFSET ?";

        List<Map<String, Object>> rows = category != null
            ? jdbcTemplate.queryForList(sql, tenantId, category, limit, offset)
            : jdbcTemplate.queryForList(sql, tenantId, limit, offset);

        List<Map<String, Object>> data = rows.stream().map(row -> Map.<String, Object>of(
                "id", row.get("id").toString(),
                "type", "community-group",
                "attributes", Map.of(
                        "name", row.get("name"),
                        "description", row.getOrDefault("description", ""),
                        "groupType", row.getOrDefault("group_type", "SUPPORT"),
                        "category", row.getOrDefault("category", ""),
                        "memberCount", row.getOrDefault("member_count", 0),
                        "isPublic", row.getOrDefault("is_public", true),
                        "status", row.getOrDefault("status", "ACTIVE")
                )
        )).toList();

        return ResponseEntity.ok(Map.of("data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/groups")
    @Transactional
    public ResponseEntity<Map<String, Object>> createGroup(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String name = body.getOrDefault("name", "").toString();
        String description = body.getOrDefault("description", "").toString();
        String groupType = body.getOrDefault("groupType", "SUPPORT").toString();
        String category = body.getOrDefault("category", "").toString();
        String createdBy = body.getOrDefault("createdBy", "").toString();

        jdbcTemplate.update("""
            INSERT INTO community_groups (id, tenant_id, name, description, group_type, category, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, tenantId, name, description, groupType, category, createdBy, now, now);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of("id", id.toString(), "type", "community-group",
                        "attributes", Map.of("name", name, "groupType", groupType, "category", category)),
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

        String memberId = body.getOrDefault("memberId", "").toString();

        jdbcTemplate.update("""
            INSERT INTO community_group_members (id, group_id, member_id, role, joined_at)
            VALUES (?, ?, ?, 'MEMBER', ?)
            ON CONFLICT (group_id, member_id) DO NOTHING
            """, UUID.randomUUID(), groupId, memberId, OffsetDateTime.now());

        jdbcTemplate.update(
                "UPDATE community_groups SET member_count = member_count + 1, updated_at = ? WHERE id = ?",
                OffsetDateTime.now(), groupId);

        return ResponseEntity.ok(Map.of("data", Map.of("joined", true),
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
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT * FROM discussion_posts WHERE group_id = ? AND tenant_id = ? AND status = 'ACTIVE'
            ORDER BY pinned DESC, created_at DESC LIMIT ? OFFSET ?
            """, groupId, tenantId, limit, offset);

        List<Map<String, Object>> data = rows.stream().map(row -> Map.<String, Object>of(
                "id", row.get("id").toString(),
                "type", "discussion-post",
                "attributes", Map.of(
                        "title", row.getOrDefault("title", ""),
                        "content", row.getOrDefault("content", ""),
                        "authorId", row.getOrDefault("author_id", ""),
                        "likesCount", row.getOrDefault("likes_count", 0),
                        "commentsCount", row.getOrDefault("comments_count", 0),
                        "pinned", row.getOrDefault("pinned", false),
                        "createdAt", row.get("created_at")
                )
        )).toList();

        return ResponseEntity.ok(Map.of("data", data,
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

        UUID id = UUID.randomUUID();
        String authorId = body.getOrDefault("authorId", "").toString();
        String title = body.getOrDefault("title", "").toString();
        String content = body.getOrDefault("content", "").toString();

        jdbcTemplate.update("""
            INSERT INTO discussion_posts (id, tenant_id, group_id, author_id, title, content, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, tenantId, groupId, authorId, title, content, OffsetDateTime.now(), OffsetDateTime.now());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of("id", id.toString(), "type", "discussion-post",
                        "attributes", Map.of("title", title, "authorId", authorId)),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
