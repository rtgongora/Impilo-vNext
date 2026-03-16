package zw.gov.mohcc.impilo.experience.controller.mobile;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.*;

/**
 * Mobile provider schedule endpoints.
 * GET /internal/v1/mobile/provider/schedule - return upcoming shifts for the provider
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/schedule")
public class MobileScheduleController {

    private final JdbcTemplate jdbcTemplate;

    public MobileScheduleController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listUpcomingShifts(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, user_id, facility_id, shift_date, start_time, end_time,
                   shift_type, status, notes, created_at, updated_at
            FROM shifts
            WHERE tenant_id = ? AND user_id = ?
              AND shift_date >= CURRENT_DATE
            ORDER BY shift_date ASC, start_time ASC
            LIMIT ? OFFSET ?
            """, tenantId, actorId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM shifts
            WHERE tenant_id = ? AND user_id = ?
              AND shift_date >= CURRENT_DATE
            """, Long.class, tenantId, actorId);

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
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
