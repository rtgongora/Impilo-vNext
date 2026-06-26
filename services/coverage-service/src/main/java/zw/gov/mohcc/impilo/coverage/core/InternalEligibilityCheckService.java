package zw.gov.mohcc.impilo.coverage.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.domain.UtilizationSummaryEntity;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;
import zw.gov.mohcc.impilo.coverage.repository.UtilizationSummaryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InternalEligibilityCheckService {

    private final CoveragePlanRepository planRepository;
    private final MemberCoverageRepository memberCoverageRepository;
    private final UtilizationSummaryRepository utilizationSummaryRepository;
    private final SubsidyEnrolmentService subsidyEnrolmentService;

    public InternalEligibilityCheckService(CoveragePlanRepository planRepository,
                                           MemberCoverageRepository memberCoverageRepository,
                                           UtilizationSummaryRepository utilizationSummaryRepository,
                                           SubsidyEnrolmentService subsidyEnrolmentService) {
        this.planRepository = planRepository;
        this.memberCoverageRepository = memberCoverageRepository;
        this.utilizationSummaryRepository = utilizationSummaryRepository;
        this.subsidyEnrolmentService = subsidyEnrolmentService;
    }

    /**
     * Subsidy cap check for a member at eligibility time. Surfaces whether an ACTIVE subsidy
     * enrolment has annual-cap headroom (Coverage owns subsidy; cap enforced here).
     */
    public SubsidyEnrolmentService.SubsidyCapResult subsidyHeadroom(
            UUID tenantId, String patientCpid, java.math.BigDecimal requestedAmount) {
        return subsidyEnrolmentService.checkCap(tenantId, patientCpid, requestedAmount);
    }

    /**
     * Active coverage + annual utilization for the requested benefit ({@code serviceCode}).
     */
    public EligibilityCheckOutcome evaluate(UUID tenantId, String patientCpid, String planCode, String serviceCode) {
        if (!hasActiveCoverage(tenantId, patientCpid, planCode)) {
            return new EligibilityCheckOutcome(false, "COVERAGE_INELIGIBLE", null, null, null);
        }
        if (serviceCode == null || serviceCode.isBlank()) {
            return new EligibilityCheckOutcome(true, "ELIGIBLE", null, null, null);
        }

        LocalDate[] window = annualWindow();
        String benefit = serviceCode.trim();
        Optional<UtilizationSummaryEntity> rowOpt = utilizationSummaryRepository
                .findByTenantIdAndMemberCpidAndPlanCodeAndBenefitCodeAndPeriodStart(
                        tenantId, patientCpid, planCode, benefit, window[0]);

        if (rowOpt.isEmpty()) {
            return new EligibilityCheckOutcome(true, "ELIGIBLE", null, null, null);
        }

        UtilizationSummaryEntity row = rowOpt.get();
        BigDecimal used = row.getUsedAmount() != null ? row.getUsedAmount() : BigDecimal.ZERO;
        BigDecimal limit = row.getLimitAmount();

        if (limit != null && used.compareTo(limit) >= 0) {
            return new EligibilityCheckOutcome(false, "UTILIZATION_LIMIT_EXCEEDED", limit, used, BigDecimal.ZERO);
        }

        if (limit != null) {
            BigDecimal remaining = limit.subtract(used);
            return new EligibilityCheckOutcome(true, "ELIGIBLE", limit, used, remaining);
        }

        return new EligibilityCheckOutcome(true, "ELIGIBLE", null, used, null);
    }

    /**
     * True when an ACTIVE member coverage row exists for the CPID on the given plan
     * and coverage dates are valid for {@link LocalDate#now()}.
     */
    public boolean isEligible(UUID tenantId, String patientCpid, String planCode) {
        return hasActiveCoverage(tenantId, patientCpid, planCode);
    }

    private boolean hasActiveCoverage(UUID tenantId, String patientCpid, String planCode) {
        Optional<CoveragePlanEntity> planOpt = planRepository.findFirstByTenantIdAndPlanCode(tenantId, planCode);
        if (planOpt.isEmpty()) {
            return false;
        }
        UUID planId = planOpt.get().getId();
        List<MemberCoverageEntity> rows =
                memberCoverageRepository.findByTenantIdAndClientIdAndStatus(tenantId, patientCpid, "ACTIVE");
        LocalDate today = LocalDate.now();
        return rows.stream().anyMatch(m -> m.getPlanId().equals(planId) && coversOn(m, today));
    }

    private static boolean coversOn(MemberCoverageEntity m, LocalDate d) {
        if (m.getEffectiveFrom() != null && d.isBefore(m.getEffectiveFrom())) {
            return false;
        }
        return m.getEffectiveTo() == null || !d.isAfter(m.getEffectiveTo());
    }

    private static LocalDate[] annualWindow() {
        int y = LocalDate.now().getYear();
        return new LocalDate[] {LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)};
    }

    public record EligibilityCheckOutcome(
            boolean eligible,
            String reason,
            BigDecimal utilizationLimit,
            BigDecimal utilizationUsed,
            BigDecimal remainingAmount
    ) {}
}
