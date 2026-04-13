package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

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
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Void> like(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader("X-Actor-ID") String actorId) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        UUID likeId = UUID.randomUUID();

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlike(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader("X-Actor-ID") String actorId) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        return ResponseEntity.ok().build();
    }

    private UUID resolvePatientId(String tenantId, String actorId) {
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
