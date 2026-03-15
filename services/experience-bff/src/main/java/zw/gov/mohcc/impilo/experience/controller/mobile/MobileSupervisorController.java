package zw.gov.mohcc.impilo.experience.controller.mobile;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.*;

/**
 * Mobile supervisor dashboard endpoints.
 * GET /internal/v1/mobile/provider/supervisor/metrics?facility_id= - facility metrics
 * GET /internal/v1/mobile/provider/supervisor/team?facility_id=    - team members
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/supervisor")
public class MobileSupervisorController {

    private final JdbcTemplate jdbcTemplate;

    public MobileSupervisorController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> facilityMetrics(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {

        Long totalEncounters = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM encounters
            WHERE tenant_id = ? AND facility_id = ?::uuid
              AND created_at >= CURRENT_DATE
            """, Long.class, tenantId, facilityId);

        Long openEncounters = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM encounters
            WHERE tenant_id = ? AND facility_id = ?::uuid
              AND status = 'IN_PROGRESS'
            """, Long.class, tenantId, facilityId);

        Long pendingTasks = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM tasks
            WHERE tenant_id = ? AND facility_id = ?::uuid
              AND status NOT IN ('COMPLETED', 'CANCELLED')
            """, Long.class, tenantId, facilityId);

        Long overdueTasks = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM tasks
            WHERE tenant_id = ? AND facility_id = ?::uuid
              AND status NOT IN ('COMPLETED', 'CANCELLED')
              AND due_at < NOW()
            """, Long.class, tenantId, facilityId);

        Long escalatedTasks = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM tasks
            WHERE tenant_id = ? AND facility_id = ?::uuid
              AND status = 'ESCALATED'
            """, Long.class, tenantId, facilityId);

        Long totalPatients = jdbcTemplate.queryForObject("""
            SELECT count(DISTINCT patient_id) FROM encounters
            WHERE tenant_id = ? AND facility_id = ?::uuid
              AND created_at >= CURRENT_DATE
            """, Long.class, tenantId, facilityId);

        Long pendingLabOrders = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM lab_orders lo
            JOIN encounters e ON lo.encounter_id = e.id
            WHERE lo.tenant_id = ? AND e.facility_id = ?::uuid
              AND lo.status IN ('ORDERED', 'COLLECTED')
            """, Long.class, tenantId, facilityId);

        Long pendingReferrals = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM referrals r
            JOIN encounters e ON r.encounter_id = e.id
            WHERE r.tenant_id = ? AND e.facility_id = ?::uuid
              AND r.status = 'PENDING'
            """, Long.class, tenantId, facilityId);

        Map<String, Object> metricsData = new LinkedHashMap<>();
        metricsData.put("encounters_today", totalEncounters != null ? totalEncounters : 0L);
        metricsData.put("open_encounters", openEncounters != null ? openEncounters : 0L);
        metricsData.put("patients_seen_today", totalPatients != null ? totalPatients : 0L);
        metricsData.put("pending_tasks", pendingTasks != null ? pendingTasks : 0L);
        metricsData.put("overdue_tasks", overdueTasks != null ? overdueTasks : 0L);
        metricsData.put("escalated_tasks", escalatedTasks != null ? escalatedTasks : 0L);
        metricsData.put("pending_lab_orders", pendingLabOrders != null ? pendingLabOrders : 0L);
        metricsData.put("pending_referrals", pendingReferrals != null ? pendingReferrals : 0L);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", facilityId,
                "type", "FacilityMetrics",
                "attributes", metricsData
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/team")
    public ResponseEntity<Map<String, Object>> teamMembers(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT au.id, au.given_name, au.family_name, au.email, au.role,
                   au.status, au.last_login_at, au.created_at,
                   (SELECT count(*) FROM tasks t
                    WHERE t.assigned_to = au.id::text AND t.tenant_id = au.tenant_id
                      AND t.status NOT IN ('COMPLETED', 'CANCELLED')) AS active_tasks,
                   (SELECT count(*) FROM encounters e
                    WHERE e.tenant_id = au.tenant_id AND e.facility_id = au.facility_id
                      AND e.created_at >= CURRENT_DATE) AS encounters_today
            FROM admin_users au
            WHERE au.tenant_id = ? AND au.facility_id = ?::uuid AND au.status = 'ACTIVE'
            ORDER BY au.family_name, au.given_name
            LIMIT ? OFFSET ?
            """, tenantId, facilityId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM admin_users
            WHERE tenant_id = ? AND facility_id = ?::uuid AND status = 'ACTIVE'
            """, Long.class, tenantId, facilityId);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("given_name", row.get("given_name"));
            attributes.put("family_name", row.get("family_name"));
            attributes.put("email", row.get("email"));
            attributes.put("role", row.get("role"));
            attributes.put("status", row.get("status"));
            attributes.put("last_login_at", row.get("last_login_at"));
            attributes.put("active_tasks", row.get("active_tasks"));
            attributes.put("encounters_today", row.get("encounters_today"));
            attributes.put("created_at", row.get("created_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "TeamMember");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

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
}
