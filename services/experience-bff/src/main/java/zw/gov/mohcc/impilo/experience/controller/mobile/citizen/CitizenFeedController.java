package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Citizen social feed endpoints.
 * GET    /internal/v1/mobile/citizen/feed
 * GET    /internal/v1/mobile/citizen/feed/{id}
 * POST   /internal/v1/mobile/citizen/feed/{id}/like
 * DELETE /internal/v1/mobile/citizen/feed/{id}/like
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/feed")
public class CitizenFeedController {

    private final JdbcTemplate jdbcTemplate;

    public CitizenFeedController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        int limit = Math.min(size, 100);
        int offset = page * limit;

        StringBuilder sql = new StringBuilder("""
            SELECT f.id, f.type, f.title, f.body, f.image_url, f.author, f.category,
                   f.published_at, f.likes_count, f.comments_count, f.action_url,
                   CASE WHEN fl.id IS NOT NULL THEN TRUE ELSE FALSE END AS liked
            FROM feed_items f
            LEFT JOIN feed_likes fl ON fl.feed_item_id = f.id AND fl.patient_id = ?
            WHERE f.tenant_id = ? AND f.active = TRUE
            """);
        List<Object> params = new ArrayList<>(List.of(patientId, tenantId));

        if (category != null && !category.isBlank()) {
            sql.append(" AND f.type = ?");
            params.add(category);
        }
        sql.append(" ORDER BY f.published_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM feed_items WHERE tenant_id = ? AND active = TRUE" +
                        (category != null && !category.isBlank() ? " AND type = '" + category + "'" : ""),
                Long.class, tenantId);

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0)));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, type, title, body, image_url, author, category,
                   published_at, likes_count, comments_count, action_url, FALSE AS liked
            FROM feed_items WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (rows.isEmpty()) throw new ResourceNotFoundException("Feed item not found: " + id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(rows.get(0)));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/like")
    @Transactional
    public ResponseEntity<Void> like(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader("X-Actor-ID") String actorId) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        UUID likeId = UUID.randomUUID();

        jdbcTemplate.update("""
            INSERT INTO feed_likes (id, tenant_id, feed_item_id, patient_id, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (feed_item_id, patient_id) DO NOTHING
            """, likeId, tenantId, id, patientId, OffsetDateTime.now());

        jdbcTemplate.update("UPDATE feed_items SET likes_count = likes_count + 1 WHERE id = ? AND tenant_id = ?",
                id, tenantId);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    @Transactional
    public ResponseEntity<Void> unlike(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader("X-Actor-ID") String actorId) {

        UUID patientId = resolvePatientId(tenantId, actorId);

        int deleted = jdbcTemplate.update(
                "DELETE FROM feed_likes WHERE feed_item_id = ? AND patient_id = ?", id, patientId);

        if (deleted > 0) {
            jdbcTemplate.update(
                    "UPDATE feed_items SET likes_count = GREATEST(0, likes_count - 1) WHERE id = ? AND tenant_id = ?",
                    id, tenantId);
        }

        return ResponseEntity.ok().build();
    }

    private UUID resolvePatientId(String tenantId, String actorId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM patients WHERE tenant_id = ? AND cpid = ?", tenantId, actorId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Patient not found for: " + actorId);
        return (UUID) rows.get(0).get("id");
    }

    private Map<String, Object> toResource(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", row.get("id").toString());
        r.put("type", row.get("type"));
        r.put("title", row.get("title"));
        r.put("body", row.get("body"));
        r.put("imageUrl", row.get("image_url"));
        r.put("author", row.get("author"));
        r.put("category", row.get("category"));
        r.put("publishedAt", row.get("published_at"));
        r.put("likesCount", row.get("likes_count"));
        r.put("commentsCount", row.get("comments_count"));
        r.put("liked", row.get("liked"));
        r.put("actionUrl", row.get("action_url"));
        return r;
    }
}
