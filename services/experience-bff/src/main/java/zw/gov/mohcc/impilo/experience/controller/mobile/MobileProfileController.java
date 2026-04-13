package zw.gov.mohcc.impilo.experience.controller.mobile;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile provider profile endpoints.
 * GET   /internal/v1/mobile/provider/profile  - get provider profile
 * PATCH /internal/v1/mobile/provider/profile  - update provider contact/profile details
 *
 * <p>STRANGLER: JdbcTemplate retained for local reads during migration; writes delegated to
 * VitoServiceClient + VarapiServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/profile")
public class MobileProfileController {

    private final VitoServiceClient vitoClient;
    private final VarapiServiceClient varapiClient;

        this.vitoClient = vitoClient;
        this.varapiClient = varapiClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PatchMapping
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> updates) {

        OffsetDateTime now = OffsetDateTime.now();

        if (updates.containsKey("phone")) {
        }
        if (updates.containsKey("email")) {
        }

        return getProfile(tenantId, requestId, correlationId, actorId);
    }
}
