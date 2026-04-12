package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Native aggregation queries for institutional financial reporting (revenue, claims, receivables, cost centers).
 */
@Repository
public class FinancialAggregateRepository {

    private final JdbcTemplate jdbc;

    public FinancialAggregateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static BigDecimal bd(ResultSet rs, String col) throws SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return v != null ? v : BigDecimal.ZERO;
    }

    public BigDecimal sumNetRevenueForMonth(UUID tenantId, UUID facilityId, int year, int month) {
        String sql = """
                SELECT COALESCE(SUM(r.net_amount), 0) FROM costa_revenue_entries r
                WHERE r.tenant_id = ? AND r.period_year = ? AND r.period_month = ?
                AND (?::uuid IS NULL OR r.facility_id = ?::uuid)
                """;
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, tenantId, year, month, facilityId, facilityId);
        return v != null ? v : BigDecimal.ZERO;
    }

    public int countDistinctEncountersForMonth(UUID tenantId, UUID facilityId, int year, int month) {
        String sql = """
                SELECT COUNT(DISTINCT r.encounter_id) FROM costa_revenue_entries r
                WHERE r.tenant_id = ? AND r.period_year = ? AND r.period_month = ?
                AND (?::uuid IS NULL OR r.facility_id = ?::uuid)
                AND r.encounter_id IS NOT NULL
                """;
        Integer n = jdbc.queryForObject(sql, Integer.class, tenantId, year, month, facilityId, facilityId);
        return n != null ? n : 0;
    }

    public List<Map<String, Object>> revenueByPayerType(UUID tenantId, UUID facilityId, int year, int month) {
        String sql = """
                SELECT COALESCE(r.payer_type, 'UNSPECIFIED') AS bucket,
                       COALESCE(SUM(r.net_amount), 0) AS total
                FROM costa_revenue_entries r
                WHERE r.tenant_id = ? AND r.period_year = ? AND r.period_month = ?
                AND (?::uuid IS NULL OR r.facility_id = ?::uuid)
                GROUP BY COALESCE(r.payer_type, 'UNSPECIFIED')
                ORDER BY total DESC
                """;
        return jdbc.query(sql, rowMapper(), tenantId, year, month, facilityId, facilityId);
    }

    public List<Map<String, Object>> revenueByDepartment(UUID tenantId, UUID facilityId, int year, int month) {
        String sql = """
                SELECT COALESCE(r.department_id, 'UNALLOCATED') AS bucket,
                       COALESCE(SUM(r.net_amount), 0) AS total
                FROM costa_revenue_entries r
                WHERE r.tenant_id = ? AND r.period_year = ? AND r.period_month = ?
                AND (?::uuid IS NULL OR r.facility_id = ?::uuid)
                GROUP BY COALESCE(r.department_id, 'UNALLOCATED')
                ORDER BY total DESC
                """;
        return jdbc.query(sql, rowMapper(), tenantId, year, month, facilityId, facilityId);
    }

    public List<Map<String, Object>> monthlyRevenueTrend(UUID tenantId, UUID facilityId, int forYear) {
        String sql = """
                SELECT r.period_month AS month,
                       COALESCE(SUM(r.net_amount), 0) AS total
                FROM costa_revenue_entries r
                WHERE r.tenant_id = ? AND r.period_year = ?
                AND (?::uuid IS NULL OR r.facility_id = ?::uuid)
                GROUP BY r.period_month
                ORDER BY r.period_month
                """;
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", rs.getInt("month"));
            m.put("total", bd(rs, "total"));
            return m;
        }, tenantId, forYear, facilityId, facilityId);
    }

    public List<Map<String, Object>> claimsByStatusForPayer(UUID tenantId, String payerId, int year, int month) {
        boolean filterPayer = payerId != null && !payerId.isBlank();
        String sql = """
                SELECT c.status AS status,
                       COUNT(*)::int AS claim_count,
                       COALESCE(SUM(b.insurer_payable), 0) AS amount
                FROM costa_claim_packs c
                JOIN costa_bill_headers b ON c.bill_id = b.bill_id
                WHERE b.tenant_id = ?
                AND EXTRACT(YEAR FROM c.created_at) = ? AND EXTRACT(MONTH FROM c.created_at) = ?
                """
                + (filterPayer ? " AND c.insurer_ref = ? " : "")
                + """
                GROUP BY c.status
                ORDER BY amount DESC
                """;
        if (filterPayer) {
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("status", rs.getString("status"));
                m.put("claimCount", rs.getInt("claim_count"));
                m.put("amount", bd(rs, "amount"));
                return m;
            }, tenantId, year, month, payerId);
        }
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", rs.getString("status"));
            m.put("claimCount", rs.getInt("claim_count"));
            m.put("amount", bd(rs, "amount"));
            return m;
        }, tenantId, year, month);
    }

    public BigDecimal sumClaimsExposureForPayer(UUID tenantId, String payerId, int year, int month) {
        boolean filterPayer = payerId != null && !payerId.isBlank();
        String sql = """
                SELECT COALESCE(SUM(b.insurer_payable), 0)
                FROM costa_claim_packs c
                JOIN costa_bill_headers b ON c.bill_id = b.bill_id
                WHERE b.tenant_id = ?
                AND EXTRACT(YEAR FROM c.created_at) = ? AND EXTRACT(MONTH FROM c.created_at) = ?
                """
                + (filterPayer ? " AND c.insurer_ref = ? " : "");
        if (filterPayer) {
            BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, tenantId, year, month, payerId);
            return v != null ? v : BigDecimal.ZERO;
        }
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, tenantId, year, month);
        return v != null ? v : BigDecimal.ZERO;
    }

    public BigDecimal sumPremiumsForPayer(UUID tenantId, String payerId, int year, int month) {
        boolean filterPayer = payerId != null && !payerId.isBlank();
        String sql = """
                SELECT COALESCE(SUM(r.net_amount), 0) FROM costa_revenue_entries r
                WHERE r.tenant_id = ? AND r.period_year = ? AND r.period_month = ?
                AND UPPER(r.revenue_type) LIKE '%PREMIUM%'
                """
                + (filterPayer ? " AND r.payer_ref = ? " : "");
        if (filterPayer) {
            BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, tenantId, year, month, payerId);
            return v != null ? v : BigDecimal.ZERO;
        }
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, tenantId, year, month);
        return v != null ? v : BigDecimal.ZERO;
    }

    public List<Map<String, Object>> providerPaymentRows(UUID tenantId, UUID providerId, int year, int month) {
        String sql = """
                SELECT p.payment_type AS payment_type,
                       COALESCE(SUM(p.paid_amount), 0) AS paid,
                       COUNT(*)::int AS payment_count
                FROM costa_payments p
                JOIN costa_bill_headers b ON p.bill_id = b.bill_id
                WHERE b.tenant_id = ? AND b.facility_id = ?::uuid
                AND p.status = 'PAID' AND p.paid_at IS NOT NULL
                AND EXTRACT(YEAR FROM p.paid_at) = ? AND EXTRACT(MONTH FROM p.paid_at) = ?
                GROUP BY p.payment_type
                ORDER BY paid DESC
                """;
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("paymentType", rs.getString("payment_type"));
            m.put("paid", bd(rs, "paid"));
            m.put("paymentCount", rs.getInt("payment_count"));
            return m;
        }, tenantId, providerId, year, month);
    }

    public BigDecimal sumProviderPayments(UUID tenantId, UUID providerId, int year, int month) {
        String sql = """
                SELECT COALESCE(SUM(p.paid_amount), 0)
                FROM costa_payments p
                JOIN costa_bill_headers b ON p.bill_id = b.bill_id
                WHERE b.tenant_id = ? AND b.facility_id = ?::uuid
                AND p.status = 'PAID' AND p.paid_at IS NOT NULL
                AND EXTRACT(YEAR FROM p.paid_at) = ? AND EXTRACT(MONTH FROM p.paid_at) = ?
                """;
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, tenantId, providerId, year, month);
        return v != null ? v : BigDecimal.ZERO;
    }

    public List<Map<String, Object>> receivablesAgingBuckets(UUID tenantId, UUID facilityId) {
        String sql = """
                SELECT COALESCE(r.aging_bucket, 'CURRENT') AS bucket,
                       COALESCE(SUM(r.outstanding), 0) AS outstanding,
                       COUNT(*)::int AS row_count
                FROM costa_receivables r
                WHERE r.tenant_id = ? AND r.status = 'OPEN'
                AND (?::uuid IS NULL OR r.facility_id = ?::uuid)
                GROUP BY COALESCE(r.aging_bucket, 'CURRENT')
                ORDER BY bucket
                """;
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bucket", rs.getString("bucket"));
            m.put("outstanding", bd(rs, "outstanding"));
            m.put("rowCount", rs.getInt("row_count"));
            return m;
        }, tenantId, facilityId, facilityId);
    }

    public List<Map<String, Object>> topDebtors(UUID tenantId, UUID facilityId, int limit) {
        String sql = """
                SELECT r.debtor_name, r.debtor_ref, r.debtor_type,
                       COALESCE(SUM(r.outstanding), 0) AS outstanding
                FROM costa_receivables r
                WHERE r.tenant_id = ? AND r.status = 'OPEN'
                AND (?::uuid IS NULL OR r.facility_id = ?::uuid)
                GROUP BY r.debtor_name, r.debtor_ref, r.debtor_type
                ORDER BY outstanding DESC
                LIMIT ?
                """;
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("debtorName", rs.getString("debtor_name"));
            m.put("debtorRef", rs.getString("debtor_ref"));
            m.put("debtorType", rs.getString("debtor_type"));
            m.put("outstanding", bd(rs, "outstanding"));
            return m;
        }, tenantId, facilityId, facilityId, limit);
    }

    public BigDecimal sumWriteOffs(UUID tenantId, UUID facilityId) {
        String sql = """
                SELECT COALESCE(SUM(w.amount), 0)
                FROM costa_write_offs w
                JOIN costa_receivables r ON w.receivable_id = r.receivable_id
                WHERE w.tenant_id = ? AND w.status IN ('PROCESSED','APPROVED','PENDING')
                AND (?::uuid IS NULL OR r.facility_id = ?::uuid)
                """;
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, tenantId, facilityId, facilityId);
        return v != null ? v : BigDecimal.ZERO;
    }

    public List<Map<String, Object>> actualSpendByCostCenterCode(UUID tenantId, UUID facilityId, int year) {
        String sql = """
                SELECT cc.code AS category_code,
                       COALESCE(SUM(cce.amount), 0) AS actual_amount
                FROM costa_cost_center_entries cce
                JOIN costa_cost_centers cc ON cce.center_id = cc.center_id
                WHERE cc.tenant_id = ? AND cc.facility_id = ?::uuid AND cce.period_year = ?
                GROUP BY cc.code
                ORDER BY actual_amount DESC
                """;
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("categoryCode", rs.getString("category_code"));
            m.put("actualAmount", bd(rs, "actual_amount"));
            return m;
        }, tenantId, facilityId, year);
    }

    private RowMapper<Map<String, Object>> rowMapper() {
        return (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bucket", rs.getString("bucket"));
            m.put("total", bd(rs, "total"));
            return m;
        };
    }
}
