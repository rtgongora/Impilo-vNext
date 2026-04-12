package zw.gov.mohcc.impilo.coverage.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.domain.PreauthRequestEntity;
import zw.gov.mohcc.impilo.coverage.domain.UtilizationSummaryEntity;
import zw.gov.mohcc.impilo.coverage.repository.UtilizationSummaryRepository;

@Service
public class PreauthUtilizationService {

    private final UtilizationSummaryRepository utilizationSummaryRepository;

    public PreauthUtilizationService(UtilizationSummaryRepository utilizationSummaryRepository) {
        this.utilizationSummaryRepository = utilizationSummaryRepository;
    }

    /**
     * @return empty if approval may proceed; non-empty denial JSON if utilization would be exceeded
     */
    public Optional<String> evaluateApproval(
            PreauthRequestEntity preauth,
            MemberCoverageEntity memberCoverage,
            String planCode,
            BigDecimal quantityToApprove) {
        if (preauth.getAnnualLimit() == null) {
            return Optional.empty();
        }
        BigDecimal qty = quantityToApprove != null ? quantityToApprove : BigDecimal.ONE;
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            qty = BigDecimal.ONE;
        }
        String benefitCode = preauth.getRequestType();
        LocalDate[] period = annualWindow();
        BigDecimal used = utilizationSummaryRepository
                .findByTenantIdAndMemberCpidAndPlanCodeAndBenefitCodeAndPeriodStart(
                        preauth.getTenantId(), memberCoverage.getClientId(), planCode, benefitCode, period[0])
                .map(UtilizationSummaryEntity::getUsedAmount)
                .orElse(BigDecimal.ZERO);
        if (used == null) {
            used = BigDecimal.ZERO;
        }
        if (used.add(qty).compareTo(preauth.getAnnualLimit()) > 0) {
            return Optional.of("{\"reason\":\"UTILIZATION_LIMIT_EXCEEDED\"}");
        }
        return Optional.empty();
    }

    @Transactional
    public void recordApprovedUsage(
            PreauthRequestEntity preauth,
            MemberCoverageEntity memberCoverage,
            String planCode,
            BigDecimal quantityApproved) {
        if (preauth.getAnnualLimit() == null) {
            return;
        }
        BigDecimal qty = quantityApproved != null ? quantityApproved : BigDecimal.ONE;
        String benefitCode = preauth.getRequestType();
        LocalDate[] period = annualWindow();
        UtilizationSummaryEntity row = utilizationSummaryRepository
                .findByTenantIdAndMemberCpidAndPlanCodeAndBenefitCodeAndPeriodStart(
                        preauth.getTenantId(), memberCoverage.getClientId(), planCode, benefitCode, period[0])
                .orElseGet(() -> new UtilizationSummaryEntity(
                        preauth.getTenantId(),
                        memberCoverage.getClientId(),
                        planCode,
                        benefitCode,
                        period[0],
                        period[1],
                        preauth.getAnnualLimit()));
        row.addUsedAmount(qty);
        utilizationSummaryRepository.save(row);
    }

    /**
     * Increments paid-claim consumption against the annual utilization row (creates row if absent).
     */
    @Transactional
    public void recordUtilization(String tenantId, String memberCpid, String planCode, String benefitCode,
                                  BigDecimal amount) {
        if (benefitCode == null || benefitCode.isBlank()) {
            return;
        }
        UUID tenantUuid = UUID.fromString(tenantId.trim());
        LocalDate[] period = annualWindow();
        String benefit = benefitCode.trim();
        BigDecimal delta = amount != null ? amount : BigDecimal.ZERO;
        UtilizationSummaryEntity row = utilizationSummaryRepository
                .findByTenantIdAndMemberCpidAndPlanCodeAndBenefitCodeAndPeriodStart(
                        tenantUuid, memberCpid, planCode, benefit, period[0])
                .orElseGet(() -> new UtilizationSummaryEntity(
                        tenantUuid, memberCpid, planCode, benefit, period[0], period[1], null));
        row.addUsedAmount(delta);
        utilizationSummaryRepository.save(row);
    }

    private static LocalDate[] annualWindow() {
        int y = LocalDate.now().getYear();
        return new LocalDate[] {LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)};
    }
}
