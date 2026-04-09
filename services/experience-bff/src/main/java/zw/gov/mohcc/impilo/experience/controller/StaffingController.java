package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Staffing surfaces: roster from recorded shifts, on-call assignments and swap workflow.
 *
 * <ul>
 *   <li>GET /internal/v1/staffing/roster-week?facility_id=&amp;week_start= — shifts overlapping ISO week (UTC day boundaries)</li>
 *   <li>GET /internal/v1/staffing/on-call?facility_id=&amp;week_start= — on-call rows for the week</li>
 *   <li>GET /internal/v1/staffing/on-call/swaps?facility_id= — swap requests</li>
 *   <li>POST /internal/v1/staffing/on-call/swaps — create swap request</li>
 *   <li>PATCH /internal/v1/staffing/on-call/swaps/{id} — approve or decline</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/staffing")
public class StaffingController {

    private final JdbcTemplate jdbcTemplate;

    public StaffingController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record PatchSwapRequest(@NotBlank String status) {}

    public record CreateSwapRequest(
            @NotBlank String facility_id,
            @NotBlank String requestor_name,
            @NotBlank String requestee_name,
            @NotBlank String original_date,
            @NotBlank String swap_date,
            String specialty
    ) {}

    @GetMapping("/roster-week")
    public ResponseEntity<Map<String, Object>> rosterWeek(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(name = "week_start") String weekStartParam,
            @RequestParam(name = "workspace_id", required = false) String workspaceId) {

        LocalDate weekStart = LocalDate.parse(weekStartParam);
        LocalDate weekEndExcl = weekStart.plusDays(7);
        Timestamp rangeStart = Timestamp.from(weekStart.atStartOfDay(ZoneOffset.UTC).toInstant());
        Timestamp rangeEnd = Timestamp.from(weekEndExcl.atStartOfDay(ZoneOffset.UTC).toInstant());

        String sql = """
                SELECT s.id, s.user_id, s.facility_id, s.workspace_id, s.status,
                       s.started_at, s.ended_at,
                       COALESCE(au.display_name, s.user_id) AS staff_display_name
                FROM shifts s
                LEFT JOIN admin_users au ON au.tenant_id = s.tenant_id
                    AND (au.username = s.user_id OR au.id::text = s.user_id)
                WHERE s.tenant_id = ?
                  AND s.facility_id = ?::uuid
                  AND s.started_at < ?
                  AND (s.ended_at IS NULL OR s.ended_at > ?)
                """;

        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(facilityId);
        params.add(rangeEnd);
        params.add(rangeStart);

        if (workspaceId != null && !workspaceId.isBlank()) {
            sql += " AND (s.workspace_id IS NULL OR s.workspace_id = ?::uuid)";
            params.add(workspaceId);
        }

        sql += " ORDER BY s.started_at ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        List<Map<String, Object>> data = rows.stream().map(this::toShiftResource).toList();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        meta.put("week_start", weekStart.toString());
        meta.put("week_end_exclusive", weekEndExcl.toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", meta);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/on-call")
    public ResponseEntity<Map<String, Object>> listOnCall(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(name = "week_start") String weekStartParam) {

        LocalDate weekStart = LocalDate.parse(weekStartParam);
        LocalDate weekEndExcl = weekStart.plusDays(7);

        String sql = """
                SELECT id, assignment_date, specialty, shift_kind,
                       primary_staff_name, primary_phone, backup_staff_name, backup_phone,
                       created_at, updated_at
                FROM on_call_assignments
                WHERE tenant_id = ?
                  AND facility_id = ?::uuid
                  AND assignment_date >= ?::date
                  AND assignment_date < ?::date
                ORDER BY assignment_date, specialty
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                tenantId, facilityId, weekStart.toString(), weekEndExcl.toString());

        List<Map<String, Object>> data = rows.stream().map(this::toOnCallResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "week_start", weekStart.toString(),
                "week_end_exclusive", weekEndExcl.toString()
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/on-call/swaps")
    public ResponseEntity<Map<String, Object>> listSwaps(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {

        String sql = """
                SELECT id, requestor_name, requestee_name, original_date, swap_date,
                       specialty, status, created_at, updated_at
                FROM on_call_swap_requests
                WHERE tenant_id = ? AND facility_id = ?::uuid
                ORDER BY created_at DESC
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId, facilityId);
        List<Map<String, Object>> data = rows.stream().map(this::toSwapResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/on-call/swaps")
    @Transactional
    public ResponseEntity<Map<String, Object>> createSwap(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody CreateSwapRequest body) {

        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO on_call_swap_requests
                    (id, tenant_id, facility_id, requestor_name, requestee_name,
                     original_date, swap_date, specialty, status, created_at, updated_at)
                VALUES (?, ?, ?::uuid, ?, ?, ?::date, ?::date, ?, 'PENDING', NOW(), NOW())
                """,
                id, tenantId, body.facility_id(),
                body.requestor_name(), body.requestee_name(),
                body.original_date(), body.swap_date(),
                body.specialty());

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT id, requestor_name, requestee_name, original_date, swap_date,
                       specialty, status, created_at, updated_at
                FROM on_call_swap_requests WHERE id = ? AND tenant_id = ?
                """, id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toSwapResource(row));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(201).body(response);
    }

    @PatchMapping("/on-call/swaps/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> patchSwap(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody PatchSwapRequest body) {

        String normalized = body.status().trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("APPROVED") && !normalized.equals("DECLINED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be APPROVED or DECLINED");
        }

        int updated = jdbcTemplate.update("""
                UPDATE on_call_swap_requests
                SET status = ?, updated_at = NOW()
                WHERE id = ?::uuid AND tenant_id = ?
                """, normalized, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Swap request not found: " + id);
        }

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT id, requestor_name, requestee_name, original_date, swap_date,
                       specialty, status, created_at, updated_at
                FROM on_call_swap_requests WHERE id = ? AND tenant_id = ?
                """, id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toSwapResource(row));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toShiftResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("user_id", Objects.toString(row.get("user_id"), ""));
        attributes.put("staff_display_name", Objects.toString(row.get("staff_display_name"), ""));
        attributes.put("facility_id", row.get("facility_id") != null ? row.get("facility_id").toString() : null);
        attributes.put("workspace_id", row.get("workspace_id") != null ? row.get("workspace_id").toString() : null);
        attributes.put("status", Objects.toString(row.get("status"), ""));
        attributes.put("started_at", row.get("started_at"));
        attributes.put("ended_at", row.get("ended_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "StaffingShift");
        resource.put("attributes", attributes);
        return resource;
    }

    private Map<String, Object> toOnCallResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("assignment_date", row.get("assignment_date").toString());
        attributes.put("specialty", Objects.toString(row.get("specialty"), ""));
        attributes.put("shift_kind", Objects.toString(row.get("shift_kind"), "24hr"));
        attributes.put("primary_staff_name", Objects.toString(row.get("primary_staff_name"), ""));
        attributes.put("primary_phone", row.get("primary_phone"));
        attributes.put("backup_staff_name", Objects.toString(row.get("backup_staff_name"), ""));
        attributes.put("backup_phone", row.get("backup_phone"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "OnCallAssignment");
        resource.put("attributes", attributes);
        return resource;
    }

    private Map<String, Object> toSwapResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("requestor_name", Objects.toString(row.get("requestor_name"), ""));
        attributes.put("requestee_name", Objects.toString(row.get("requestee_name"), ""));
        attributes.put("original_date", row.get("original_date").toString());
        attributes.put("swap_date", row.get("swap_date").toString());
        attributes.put("specialty", row.get("specialty"));
        attributes.put("status", Objects.toString(row.get("status"), "PENDING"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "OnCallSwapRequest");
        resource.put("attributes", attributes);
        return resource;
    }
}
