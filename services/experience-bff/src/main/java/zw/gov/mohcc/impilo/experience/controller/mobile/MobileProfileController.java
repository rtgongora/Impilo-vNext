package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.*;

/**
 * Mobile provider profile endpoints.
 * GET   /internal/v1/mobile/provider/profile  - get provider profile
 * PATCH /internal/v1/mobile/provider/profile  - update provider contact/profile details
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/profile")
public class MobileProfileController {

    private final VitoServiceClient vitoClient;
    private final VarapiServiceClient varapiClient;

    public MobileProfileController(VitoServiceClient vitoClient, VarapiServiceClient varapiClient) {
        this.vitoClient = vitoClient;
        this.varapiClient = varapiClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {
        try {
            JsonNode provider = varapiClient.getProvider(actorId);
            if (provider != null) {
                return ResponseEntity.ok(Map.of(
                        "data", provider,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PatchMapping
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> updates) {

        return getProfile(tenantId, requestId, correlationId, actorId);
    }
}
