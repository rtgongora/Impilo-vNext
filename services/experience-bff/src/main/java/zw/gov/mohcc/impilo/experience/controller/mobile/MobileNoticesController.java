package zw.gov.mohcc.impilo.experience.controller.mobile;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/**
 * Mobile provider notices/alerts endpoints.
 * GET /internal/v1/mobile/provider/notices - return professional notices for the provider
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/notices")
public class MobileNoticesController {

    private final VarapiServiceClient varapiClient;

    public MobileNoticesController(VarapiServiceClient varapiClient) {
        this.varapiClient = varapiClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listNotices(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode notices = varapiClient.getProviderNotices(actorId);
            return ResponseEntity.ok(Map.of(
                    "data", notices != null ? notices : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId, "page", page, "size", size)));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
