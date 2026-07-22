package zw.gov.mohcc.impilo.coverage.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.ApiResponse;

/**
 * Covered-care seam: resolves a member's in-network providers so find-care results can be
 * flagged in / out of network. Networks are payer-scoped, so the chain is
 * member → active coverage → plan → payer → the payer's networks → their member providers.
 * All real data — an unlinked member simply returns an empty network set.
 */
@RestController
@RequestMapping("/internal/v1/coverage")
public class CoveredCareController {

    private final JdbcTemplate jdbc;

    public CoveredCareController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/networks/for-member")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> forMember(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam("member_cpid") String memberCpid) {
        UUID tid = UUID.fromString(tenantId);

        String sql = "SELECT n.network_id, n.network_type, m.provider_id "
                + "FROM cv_member_coverage mc "
                + "JOIN cv_coverage_plans p ON p.id = mc.plan_id "
                + "JOIN cv_provider_networks n ON n.payer_id = p.payer_id AND n.tenant_id = mc.tenant_id "
                + "JOIN cv_network_members m ON m.network_id = n.network_id "
                + "WHERE mc.tenant_id = ? AND mc.client_id = ?";

        Map<String, Map<String, Object>> networks = new LinkedHashMap<>();
        Set<String> allProviders = new LinkedHashSet<>();
        jdbc.query(sql, rs -> {
            String nid = rs.getString("network_id");
            String provider = rs.getString("provider_id");
            Map<String, Object> net = networks.computeIfAbsent(nid, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("networkId", nid);
                m.put("networkType", rs2type(rs));
                m.put("providerIds", new ArrayList<String>());
                return m;
            });
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) net.get("providerIds");
            ids.add(provider);
            allProviders.add(provider);
        }, tid, memberCpid);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("networks", new ArrayList<>(networks.values()));
        out.put("inNetworkProviderIds", new ArrayList<>(allProviders));
        return ResponseEntity.ok(ApiResponse.of(out));
    }

    private static String rs2type(java.sql.ResultSet rs) {
        try {
            return rs.getString("network_type");
        } catch (java.sql.SQLException e) {
            return null;
        }
    }
}
