package zw.gov.mohcc.impilo.tuso.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cross-facility data-gap worklist (HAR W6).
 *
 * <p>{@link FacilityDataGapController} is scoped to one facility, which is right for a facility
 * administrator and useless for a steward: the HPA import produced <b>24,616</b> open tasks spread
 * across 5,509 facilities, and the only way to reach them was to open each facility in turn. The
 * work existed and was, in practice, unreachable.</p>
 *
 * <p>This is a read + bulk-transition surface over the same table, filtered by the axes a steward
 * actually works along — dimension, responsible actor, priority, province — and paged, because an
 * unbounded query over 24,616 rows is a memory-pressure vector, not a worklist.</p>
 *
 * <p><b>Bulk still means audited.</b> Every transition appends to the same
 * {@code resolution_history} the single-task path writes, with the actor and note. Bulk-resolving
 * is faster, not quieter — and it can only move a task's status. Nothing here marks anything
 * verified, because a data gap is closed by evidence arriving, not by a steward clearing a row.</p>
 */
@RestController
@RequestMapping("/v1/internal/facility-registry/data-gap-worklist")
public class FacilityDataGapWorklistController {

    private static final Logger log = LoggerFactory.getLogger(FacilityDataGapWorklistController.class);

    /** Anything larger stops being a worklist and starts being an export. */
    private static final int MAX_PAGE_SIZE = 200;

    /** A bulk transition is a considered action over a screenful, not a table-wide sweep. */
    static final int MAX_BULK_TASKS = 200;

    /** The statuses a steward may move a task to. RESOLVED is here; VERIFIED deliberately is not. */
    static final List<String> ALLOWED_STATUSES = List.of("OPEN", "IN_PROGRESS", "RESOLVED", "DISMISSED");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public FacilityDataGapWorklistController(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record BulkResolveRequest(List<Long> taskIds, String status, String note) {}

    /** Paged, filterable worklist with the facility context needed to act on a row. */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> worklist(
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String responsibleActor,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String taskType,
            @RequestParam(defaultValue = "OPEN") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        TrustContext ctx = TrustContextHolder.require();
        int bounded = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = Math.max(page, 0) * bounded;

        String where = """
                 WHERE t.tenant_id = ?
                   AND (CAST(? AS text) IS NULL OR t.status = ?)
                   AND (CAST(? AS text) IS NULL OR t.dimension = ?)
                   AND (CAST(? AS text) IS NULL OR t.responsible_actor = ?)
                   AND (CAST(? AS text) IS NULL OR t.priority = ?)
                   AND (CAST(? AS text) IS NULL OR t.task_type = ?)
                   AND (CAST(? AS text) IS NULL OR lower(f.province) = lower(?))
                """;
        Object[] args = new Object[]{
                ctx.tenantId(),
                blankToNull(status), blankToNull(status),
                blankToNull(dimension), blankToNull(dimension),
                blankToNull(responsibleActor), blankToNull(responsibleActor),
                blankToNull(priority), blankToNull(priority),
                blankToNull(taskType), blankToNull(taskType),
                blankToNull(province), blankToNull(province)};

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM tuso.facility_data_gap_task t "
                        + "JOIN tuso.facility f ON f.id = t.facility_id " + where,
                Long.class, args);

        Object[] pagedArgs = new Object[args.length + 2];
        System.arraycopy(args, 0, pagedArgs, 0, args.length);
        pagedArgs[args.length] = bounded;
        pagedArgs[args.length + 1] = offset;

        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT t.id, t.facility_id, f.facility_uuid, f.facility_code, f.name AS facility_name, "
                        + "       f.province, f.district, f.regulatory_status, "
                        + "       t.task_type, t.dimension, t.required_information, t.why_required, "
                        + "       t.responsible_actor, t.accepted_evidence, t.priority, t.visibility, "
                        + "       t.status, t.updated_at "
                        + "  FROM tuso.facility_data_gap_task t "
                        + "  JOIN tuso.facility f ON f.id = t.facility_id "
                        + where
                        + " ORDER BY CASE t.priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, "
                        + "          f.province, f.name, t.id "
                        + " LIMIT ? OFFSET ?",
                pagedArgs);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", Math.max(page, 0));
        body.put("size", bounded);
        body.put("totalElements", total == null ? 0L : total);
        return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
    }

    /** Counts per dimension and per province — what a steward needs to decide where to start. */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @RequestParam(defaultValue = "OPEN") String status) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("byDimension", jdbc.queryForList(
                "SELECT t.dimension, t.priority, count(*) AS task_count "
                        + "  FROM tuso.facility_data_gap_task t "
                        + " WHERE t.tenant_id = ? AND (CAST(? AS text) IS NULL OR t.status = ?) "
                        + " GROUP BY t.dimension, t.priority ORDER BY count(*) DESC",
                ctx.tenantId(), blankToNull(status), blankToNull(status)));
        body.put("byProvince", jdbc.queryForList(
                "SELECT f.province, count(*) AS task_count, count(DISTINCT f.id) AS facility_count "
                        + "  FROM tuso.facility_data_gap_task t "
                        + "  JOIN tuso.facility f ON f.id = t.facility_id "
                        + " WHERE t.tenant_id = ? AND (CAST(? AS text) IS NULL OR t.status = ?) "
                        + " GROUP BY f.province ORDER BY count(*) DESC",
                ctx.tenantId(), blankToNull(status), blankToNull(status)));
        return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
    }

    /**
     * Move a bounded set of tasks to a new status, appending the same audit entry the single-task
     * path writes. Tenant-scoped in the WHERE clause, so a task id from another tenant is silently
     * not updated rather than acted on.
     */
    @PostMapping("/bulk-transition")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkTransition(
            @RequestBody BulkResolveRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (request == null || request.taskIds() == null || request.taskIds().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "NO_TASKS", "At least one task id is required", 400, ctx.correlationId().toString()));
        }
        if (request.taskIds().size() > MAX_BULK_TASKS) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "TOO_MANY_TASKS",
                    "At most " + MAX_BULK_TASKS + " tasks may be transitioned at once",
                    400, ctx.correlationId().toString()));
        }
        String status = request.status() == null ? "IN_PROGRESS" : request.status().trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "INVALID_STATUS",
                    "Status must be one of " + ALLOWED_STATUSES + " — a data gap is closed by evidence, "
                            + "so nothing here can mark a facility verified",
                    400, ctx.correlationId().toString()));
        }

        String entry;
        try {
            entry = objectMapper.writeValueAsString(Map.of(
                    "status", status,
                    "by", ctx.actorId() == null ? "" : ctx.actorId(),
                    "note", request.note() == null ? "" : request.note(),
                    "bulk", true));
        } catch (Exception e) {
            entry = "{}";
        }

        int updated = 0;
        for (Long taskId : request.taskIds()) {
            if (taskId == null) {
                continue;
            }
            updated += jdbc.update(
                    "UPDATE tuso.facility_data_gap_task SET status = ?, updated_at = now(), "
                            + "resolution_history = resolution_history || ?::jsonb "
                            + " WHERE id = ? AND tenant_id = ?",
                    status, "[" + entry + "]", taskId, ctx.tenantId());
        }
        log.info("Data-gap bulk transition to {} by {}: requested={} updated={}",
                status, ctx.actorId(), request.taskIds().size(), updated);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("requested", request.taskIds().size(), "updated", updated, "status", status),
                ctx.correlationId().toString()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
