package zw.gov.mohcc.impilo.coverage.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.ApiResponse;

/**
 * Aggregated performance summary for the Ruvimbo Performance workspace.
 *
 * Every figure is a live, tenant-scoped COUNT / GROUP BY over the coverage tables — never
 * an estimate or a fabricated statistic. Status columns are indexed, so these are cheap
 * reads. Table names are fixed constants (no user input reaches the SQL), so the string
 * concatenation carries no injection risk.
 */
@RestController
@RequestMapping("/internal/v1/coverage")
public class PerformanceSummaryController {

    private final JdbcTemplate jdbc;

    public PerformanceSummaryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/performance/summary")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        UUID tid = UUID.fromString(tenantId);

        Map<String, Object> out = new LinkedHashMap<>();

        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("totalMembers", count("cv_member_coverage", tid));
        coverage.put("activeMembers", countWhere("cv_member_coverage", tid, "status = 'ACTIVE'"));
        out.put("coverage", coverage);

        out.put("claims", statusBlock("cv_claims", tid));
        out.put("authorisations", statusBlock("cv_authorisations", tid));
        out.put("switch", statusBlock("cv_gateway_transactions", tid));

        Map<String, Object> integrity = new LinkedHashMap<>();
        integrity.put("openFlags", countWhere("cv_fraud_flags", tid, "status = 'OPEN'"));
        out.put("integrity", integrity);

        Map<String, Object> ecosystem = new LinkedHashMap<>();
        ecosystem.put("payers", count("cv_payers", tid));
        ecosystem.put("activePayers", countWhere("cv_payers", tid, "status = 'ACTIVE'"));
        ecosystem.put("employers", count("cv_employers", tid));
        out.put("ecosystem", ecosystem);

        return ResponseEntity.ok(ApiResponse.of(out));
    }

    private long count(String table, UUID tid) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Long.class, tid);
        return n == null ? 0L : n;
    }

    private long countWhere(String table, UUID tid, String cond) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND " + cond, Long.class, tid);
        return n == null ? 0L : n;
    }

    private Map<String, Object> statusBlock(String table, UUID tid) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long[] total = {0L};
        jdbc.query("SELECT status, COUNT(*) AS n FROM " + table + " WHERE tenant_id = ? GROUP BY status",
                rs -> {
                    String st = rs.getString("status");
                    byStatus.put(st == null ? "UNKNOWN" : st, rs.getLong("n"));
                    total[0] += rs.getLong("n");
                }, tid);
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("total", total[0]);
        block.put("byStatus", byStatus);
        return block;
    }
}
