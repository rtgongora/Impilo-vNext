package zw.gov.mohcc.impilo.coverage.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InternalEligibilityCheckService {

    private final CoveragePlanRepository planRepository;
    private final MemberCoverageRepository memberCoverageRepository;

    public InternalEligibilityCheckService(CoveragePlanRepository planRepository,
                                           MemberCoverageRepository memberCoverageRepository) {
        this.planRepository = planRepository;
        this.memberCoverageRepository = memberCoverageRepository;
    }

    /**
     * True when an ACTIVE member coverage row exists for the CPID on the given plan
     * and coverage dates are valid for {@link LocalDate#now()}.
     */
    public boolean isEligible(UUID tenantId, String patientCpid, String planCode) {
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
}
