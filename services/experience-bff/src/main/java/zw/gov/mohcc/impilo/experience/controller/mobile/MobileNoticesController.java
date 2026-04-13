package zw.gov.mohcc.impilo.experience.controller.mobile;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.*;

/**
 * Mobile provider notices/alerts endpoints.
 * GET /internal/v1/mobile/provider/notices - return professional notices for the provider
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/notices")
public class MobileNoticesController {

    public MobileNoticesController() {
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listNotices(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    private Map<String, Object> toResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("title", row.get("title"));
        attributes.put("body", row.get("body"));
        attributes.put("category", row.get("category"));
        attributes.put("priority", row.get("priority"));
        attributes.put("published_at", row.get("published_at"));
        attributes.put("expires_at", row.get("expires_at"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "ProviderNotice");
        resource.put("attributes", attributes);
        return resource;
    }
}
