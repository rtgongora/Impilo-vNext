package zw.gov.mohcc.impilo.experience.controller.mobile;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.*;

/**
 * Mobile provider schedule endpoints.
 * GET /internal/v1/mobile/provider/schedule - return upcoming shifts for the provider
 *
 * <p>STRANGLER: JdbcTemplate retained for local reads during migration; writes delegated to TusoServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/schedule")
public class MobileScheduleController {

    private final TusoServiceClient tusoClient;

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
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    private Map<String, Object> toResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("user_id", row.get("user_id"));
        attributes.put("facility_id", row.get("facility_id") != null ? row.get("facility_id").toString() : null);
        attributes.put("shift_date", row.get("shift_date"));
        attributes.put("start_time", row.get("start_time"));
        attributes.put("end_time", row.get("end_time"));
        attributes.put("shift_type", row.get("shift_type"));
        attributes.put("status", row.get("status"));
        attributes.put("notes", row.get("notes"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "Shift");
        resource.put("attributes", attributes);
        return resource;
    }
}
