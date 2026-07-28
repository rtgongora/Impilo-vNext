package zw.gov.mohcc.impilo.varapi.core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The student registration reports (NCZ-W1D), executed where the data lives.
 *
 * <p><b>Why they are not in reporting-service.</b> Five of the seven ACTIVE governed report
 * definitions in {@code rpt_report_definitions} embed SQL against {@code varapi.*} — and the
 * reporting database has no {@code varapi} schema, no {@code postgres_fdw} and no {@code dblink}.
 * Verified on the live estate, not inferred. Those definitions are registry rows that cannot run:
 * a report nobody can execute is not a report, and adding a sixth would have looked like progress
 * while producing nothing. So these queries live in the service that owns the tables, and
 * reporting-service catalogues them by reference rather than by embedding SQL it cannot run.</p>
 *
 * <p><b>The lie this class is written to avoid.</b> "Awaiting payment" is the count a council will
 * act on, and it must never include an applicant whose fee the Council has not set. A fee nobody
 * can pay is not an applicant who has not paid — conflating them would show a queue of people to
 * chase when the outstanding action is the Council's own. {@code awaitingCouncilFeeDecision} is
 * therefore a separate line, and the two are computed from different sources so they cannot drift
 * into one another.</p>
 */
@Service
public class StudentRegistrationReportService {

    private final CouncilFeeGate feeGate;

    @PersistenceContext
    private EntityManager entityManager;

    public StudentRegistrationReportService(CouncilFeeGate feeGate) {
        this.feeGate = feeGate;
    }

    /**
     * The regulator's operational board. Every number below is a count of rows that exist, not an
     * estimate — and each is attributable to a specific table so a disputed figure can be traced.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> regulatorBoard(UUID tenantId, Long councilId, String applicationType) {
        Map<String, Object> board = new LinkedHashMap<>();

        board.put("submitted", count(tenantId, councilId, applicationType,
                "a.workflow_state = 'SUBMITTED'"));
        board.put("underReview", count(tenantId, councilId, applicationType,
                "a.workflow_state IN ('UNDER_ADMIN_REVIEW','AWAITING_VERIFICATION')"));

        // Awaiting the APPLICANT: at least one section has been sent back to them.
        board.put("awaitingApplicant", scalar(
                "SELECT count(DISTINCT a.id) FROM varapi.provider_applications a "
                        + "JOIN varapi.application_section s ON s.application_id = a.id "
                        + "WHERE a.tenant_id = :tenant AND a.council_id = :council "
                        + "AND a.application_type = :type AND s.state = 'RETURNED'",
                tenantId, councilId, applicationType));

        // Awaiting the INSTITUTION: an invitation is out and has not been completed.
        board.put("awaitingInstitution", scalar(
                "SELECT count(DISTINCT a.id) FROM varapi.provider_applications a "
                        + "JOIN varapi.application_contributor_invite i ON i.application_id = a.id "
                        + "WHERE a.tenant_id = :tenant AND a.council_id = :council "
                        + "AND a.application_type = :type AND i.status IN ('INVITED','ACCEPTED')",
                tenantId, councilId, applicationType));

        // THE DISTINCTION. An applicant who has not paid is a person to chase. An applicant whose
        // fee the Council has not set is the COUNCIL's outstanding action, and putting them in the
        // same bucket would send someone chasing a payment that cannot be made.
        CouncilFeeGate.FeeVerdict fee = feeGate.verdictFor(tenantId, councilId, "STUDENT_INDEX");
        if (fee.allowsChargeToProceed()) {
            board.put("awaitingPayment", count(tenantId, councilId, applicationType,
                    "a.fee_state IS DISTINCT FROM 'PAID'"));
            board.put("awaitingCouncilFeeDecision", 0L);
            board.put("feeStatus", "SET");
        } else {
            // Nobody can be awaiting payment for a fee that has no amount.
            board.put("awaitingPayment", 0L);
            board.put("awaitingCouncilFeeDecision", count(tenantId, councilId, applicationType,
                    "a.fee_state IS DISTINCT FROM 'PAID'"));
            board.put("feeStatus", fee.verdict().name());
            board.put("feeDecisionRef", fee.decisionRef());
            board.put("feeExplanation", fee.explain());
        }

        board.put("admitted", scalar(
                "SELECT count(*) FROM varapi.provider_council_registration_records r "
                        + "JOIN varapi.professional_registers p ON p.id = r.register_id "
                        + "WHERE r.tenant_id = :tenant AND r.council_id = :council "
                        + "AND r.status = 'ADMITTED' AND p.register_axis = 'STANDING' "
                        + "AND :type = :type",
                tenantId, councilId, applicationType));

        board.put("indexNumbersIssued", scalar(
                "SELECT count(*) FROM varapi.issued_number n "
                        + "JOIN varapi.numbering_policy p ON p.id = n.policy_id "
                        + "WHERE n.tenant_id = :tenant AND p.council_id = :council "
                        + "AND n.state = 'ISSUED' AND :type = :type",
                tenantId, councilId, applicationType));

        return board;
    }

    /**
     * Ageing, as whole days since submission. Reported in bands rather than an average, because an
     * average hides the case that has been waiting four months behind fifty that took a week.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> ageing(UUID tenantId, Long councilId, String applicationType) {
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT band, count(*) FROM ("
                                + "  SELECT CASE "
                                + "    WHEN NOW() - a.submitted_at < INTERVAL '7 days'  THEN '0-6 days' "
                                + "    WHEN NOW() - a.submitted_at < INTERVAL '30 days' THEN '7-29 days' "
                                + "    WHEN NOW() - a.submitted_at < INTERVAL '90 days' THEN '30-89 days' "
                                + "    ELSE '90+ days' END AS band "
                                + "  FROM varapi.provider_applications a "
                                + "  WHERE a.tenant_id = :tenant AND a.council_id = :council "
                                + "    AND a.application_type = :type AND a.submitted_at IS NOT NULL "
                                + "    AND a.closed_at IS NULL) b "
                                + "GROUP BY band ORDER BY band")
                .setParameter("tenant", tenantId)
                .setParameter("council", councilId)
                .setParameter("type", applicationType)
                .getResultList();

        return rows.stream().map(r -> {
            Object[] row = (Object[]) r;
            Map<String, Object> band = new LinkedHashMap<>();
            band.put("band", String.valueOf(row[0]));
            band.put("count", ((Number) row[1]).longValue());
            return band;
        }).toList();
    }

    /**
     * How often work is being sent back, by section. A section returned far more than the others is
     * usually a form problem rather than an applicant problem, and that is worth a council seeing.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> returnsBySection(UUID tenantId, Long councilId,
                                                      String applicationType) {
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT s.section_key, s.label, sum(s.return_count) AS returns, "
                                + "count(*) FILTER (WHERE s.state = 'RETURNED') AS open_returns "
                                + "FROM varapi.application_section s "
                                + "JOIN varapi.provider_applications a ON a.id = s.application_id "
                                + "WHERE s.tenant_id = :tenant AND a.council_id = :council "
                                + "AND a.application_type = :type "
                                + "GROUP BY s.section_key, s.label ORDER BY returns DESC")
                .setParameter("tenant", tenantId)
                .setParameter("council", councilId)
                .setParameter("type", applicationType)
                .getResultList();

        return rows.stream().map(r -> {
            Object[] row = (Object[]) r;
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("sectionKey", String.valueOf(row[0]));
            section.put("label", String.valueOf(row[1]));
            section.put("totalReturns", row[2] == null ? 0L : ((Number) row[2]).longValue());
            section.put("openReturns", row[3] == null ? 0L : ((Number) row[3]).longValue());
            return section;
        }).toList();
    }

    /** The institution's own view: what has been asked of them, and what is outstanding. */
    @Transactional(readOnly = true)
    public Map<String, Object> institutionBoard(UUID tenantId, UUID contributorOrgId) {
        Map<String, Object> board = new LinkedHashMap<>();
        for (String status : List.of("INVITED", "ACCEPTED", "COMPLETED", "EXPIRED")) {
            board.put(status.toLowerCase(), scalarByOrg(
                    "SELECT count(*) FROM varapi.application_contributor_invite "
                            + "WHERE tenant_id = :tenant AND contributor_org_id = :org "
                            + "AND status = '" + status + "'",
                    tenantId, contributorOrgId));
        }
        return board;
    }

    private long count(UUID tenantId, Long councilId, String applicationType, String predicate) {
        return scalar("SELECT count(*) FROM varapi.provider_applications a "
                + "WHERE a.tenant_id = :tenant AND a.council_id = :council "
                + "AND a.application_type = :type AND " + predicate, tenantId, councilId, applicationType);
    }

    private long scalar(String sql, UUID tenantId, Long councilId, String applicationType) {
        Object value = entityManager.createNativeQuery(sql)
                .setParameter("tenant", tenantId)
                .setParameter("council", councilId)
                .setParameter("type", applicationType)
                .getSingleResult();
        return value == null ? 0L : ((Number) value).longValue();
    }

    private long scalarByOrg(String sql, UUID tenantId, UUID orgId) {
        Object value = entityManager.createNativeQuery(sql)
                .setParameter("tenant", tenantId)
                .setParameter("org", orgId)
                .getSingleResult();
        return value == null ? 0L : ((Number) value).longValue();
    }
}
