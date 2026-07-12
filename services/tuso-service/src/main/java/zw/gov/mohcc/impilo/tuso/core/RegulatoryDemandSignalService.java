package zw.gov.mohcc.impilo.tuso.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate regulatory demand signal for the marketplace supply side.
 *
 * <p>STRICTLY AGGREGATE: counts of open findings / open corrective actions by
 * checklist requirement code + province, and open applications by facility
 * type. No facility, application, practitioner or certificate identifier ever
 * appears in the response — individual facilities' compliance gaps are
 * commercially sensitive. Small cells (count &lt; {@value #SMALL_CELL_FLOOR})
 * are suppressed to prevent re-identification in sparse provinces.</p>
 */
@Service
public class RegulatoryDemandSignalService {

    static final int SMALL_CELL_FLOOR = 3;

    private final JdbcTemplate jdbcTemplate;

    public RegulatoryDemandSignalService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CodeProvinceCount(String checklistItemCode, String province, long count) {}
    public record TypeCount(String facilityType, long count) {}
    public record DemandSignalResponse(Instant generatedAt,
                                       int smallCellFloor,
                                       List<CodeProvinceCount> openFindings,
                                       List<CodeProvinceCount> openComplianceActions,
                                       List<TypeCount> openApplicationsByFacilityType) {}

    @Transactional(readOnly = true)
    public DemandSignalResponse demandSignal() {
        List<CodeProvinceCount> findings = jdbcTemplate.query("""
                SELECT f.checklist_item_code, COALESCE(fac.province, 'UNKNOWN') AS province, count(*) AS n
                  FROM tuso.inspection_finding f
                  JOIN tuso.facility_inspection i ON i.inspection_id = f.inspection_id
                  JOIN tuso.facility fac ON fac.id = i.facility_id
                 WHERE f.rectification_status = 'OPEN'
                 GROUP BY 1, 2
                HAVING count(*) >= ?
                 ORDER BY n DESC
                """,
                (rs, n) -> new CodeProvinceCount(rs.getString(1), rs.getString(2), rs.getLong(3)),
                SMALL_CELL_FLOOR);

        List<CodeProvinceCount> actions = jdbcTemplate.query("""
                SELECT f.checklist_item_code, COALESCE(fac.province, 'UNKNOWN') AS province, count(*) AS n
                  FROM tuso.compliance_action a
                  JOIN tuso.inspection_finding f ON f.finding_id = a.inspection_finding_id
                  JOIN tuso.facility fac ON fac.id = a.facility_id
                 WHERE a.status = 'OPEN'
                 GROUP BY 1, 2
                HAVING count(*) >= ?
                 ORDER BY n DESC
                """,
                (rs, n) -> new CodeProvinceCount(rs.getString(1), rs.getString(2), rs.getLong(3)),
                SMALL_CELL_FLOOR);

        List<TypeCount> applications = jdbcTemplate.query("""
                SELECT COALESCE(fac.facility_type, 'UNKNOWN') AS facility_type, count(*) AS n
                  FROM tuso.facility_application app
                  JOIN tuso.facility fac ON fac.id = app.facility_id
                 WHERE app.current_workflow_state NOT IN
                       ('DECIDED_APPROVED', 'DECIDED_REJECTED', 'CLOSED_OUT', 'WITHDRAWN')
                 GROUP BY 1
                HAVING count(*) >= ?
                 ORDER BY n DESC
                """,
                (rs, n) -> new TypeCount(rs.getString(1), rs.getLong(2)),
                SMALL_CELL_FLOOR);

        return new DemandSignalResponse(Instant.now(), SMALL_CELL_FLOOR, findings, actions, applications);
    }
}
