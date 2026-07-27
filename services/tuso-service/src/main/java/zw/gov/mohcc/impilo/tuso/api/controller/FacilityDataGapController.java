package zw.gov.mohcc.impilo.tuso.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facility completeness dimensions + data-gap / verification tasks (V036, the HPA
 * enrichment layer). A facility admin (or steward) sees, per dimension, what is
 * complete/partial/missing, and works the actionable tasks — each with a required
 * information item, responsible actor, accepted evidence, priority and resolution
 * history. Resolving a task appends to its history; it never fakes verification.
 *
 * <p>Path carries {@code /facility-data-gaps} so the tshepo-authz seed
 * (V042) gates it for SYSTEM_ADMIN / HIE_ADMIN and the facility-scoped FACILITY_ADMIN.</p>
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityUuid}/facility-data-gaps")
public class FacilityDataGapController {

    private static final Logger log = LoggerFactory.getLogger(FacilityDataGapController.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public FacilityDataGapController(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record ResolveRequest(String status, String note) {}

    /** Completeness by dimension (identity / regulatory / location / contact / …). */
    @GetMapping("/completeness")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> completeness(@PathVariable String facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        Long facilityId = resolveFacilityId(facilityUuid, ctx.tenantId());
        if (facilityId == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of(), ctx.correlationId().toString()));
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT dimension, status, completeness_pct, missing_indicators, computed_at "
                        + "FROM tuso.facility_completeness_dimension WHERE facility_id=? ORDER BY dimension",
                facilityId);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    /** Open + resolved data-gap / verification tasks for the facility. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> tasks(@PathVariable String facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        Long facilityId = resolveFacilityId(facilityUuid, ctx.tenantId());
        if (facilityId == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of(), ctx.correlationId().toString()));
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, task_type, dimension, required_information, why_required, responsible_actor, "
                        + "accepted_evidence, priority, visibility, status, updated_at "
                        + "FROM tuso.facility_data_gap_task WHERE facility_id=? "
                        + "ORDER BY (status='OPEN') DESC, priority, id",
                facilityId);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    /** Move a task's status forward (OPEN → IN_PROGRESS → RESOLVED / DISMISSED) with an audit note. */
    @PostMapping("/{taskId}/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolve(
            @PathVariable String facilityUuid, @PathVariable long taskId, @RequestBody ResolveRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        Long facilityId = resolveFacilityId(facilityUuid, ctx.tenantId());
        if (facilityId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                    "FACILITY_NOT_FOUND", "Facility not found", 404, ctx.correlationId().toString()));
        }
        String status = request.status() != null ? request.status() : "IN_PROGRESS";
        String entry;
        try {
            entry = objectMapper.writeValueAsString(Map.of(
                    "status", status, "by", ctx.actorId(),
                    "note", request.note() != null ? request.note() : ""));
        } catch (Exception e) {
            entry = "{}";
        }
        int updated = jdbc.update(
                "UPDATE tuso.facility_data_gap_task SET status=?, updated_at=now(), "
                        + "resolution_history = resolution_history || ?::jsonb "
                        + "WHERE id=? AND facility_id=?",
                status, "[" + entry + "]", taskId, facilityId);
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                    "TASK_NOT_FOUND", "Data-gap task not found: " + taskId, 404, ctx.correlationId().toString()));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", taskId, "status", status),
                ctx.correlationId().toString()));
    }

    /**
     * Resolve the numeric facility id <b>within the caller's tenant</b>.
     *
     * <p>This lookup previously had no tenant predicate, so a caller holding a facility UUID from
     * another tenant resolved it successfully and could then read or mutate that facility's
     * data-gap tasks — the per-task {@code WHERE ... AND facility_id=?} guard is not a tenant
     * boundary, only a facility one. Every other facility service in TUSO passes tenant through a
     * {@code requireFacility(uuid, tenantId)} guard; this one is now consistent with them. An
     * out-of-tenant UUID resolves to null and the caller receives the same 404 as a UUID that does
     * not exist, so nothing is disclosed either way.</p>
     */
    private Long resolveFacilityId(String facilityUuid, UUID tenantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM tuso.facility WHERE facility_uuid=?::uuid AND tenant_id=?",
                    Long.class, facilityUuid, tenantId);
        } catch (Exception e) {
            return null;
        }
    }
}
