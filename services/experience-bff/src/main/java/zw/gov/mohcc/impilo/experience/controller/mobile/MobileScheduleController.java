package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.*;

/**
 * Mobile provider schedule endpoints.
 * GET /internal/v1/mobile/provider/schedule - return upcoming shifts for the provider
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/schedule")
public class MobileScheduleController {

    private final TusoServiceClient tusoClient;

    public MobileScheduleController(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listUpcomingShifts(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode data = tusoClient.getCurrentShift(actorId);
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
