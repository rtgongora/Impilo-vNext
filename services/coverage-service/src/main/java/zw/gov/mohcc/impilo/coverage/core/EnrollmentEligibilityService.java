package zw.gov.mohcc.impilo.coverage.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.coverage.api.dto.EnrollmentEligibilityResponse;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EnrollmentEligibilityService {

    private final CoveragePlanRepository planRepository;
    private final MemberCoverageRepository memberCoverageRepository;

    public EnrollmentEligibilityService(CoveragePlanRepository planRepository,
                                        MemberCoverageRepository memberCoverageRepository) {
        this.planRepository = planRepository;
        this.memberCoverageRepository = memberCoverageRepository;
    }

    public EnrollmentEligibilityResponse evaluate(UUID tenantId, String clientId, UUID planId) {
        Optional<CoveragePlanEntity> planOpt = planRepository.findById(planId)
                .filter(p -> p.getTenantId().equals(tenantId));
        if (planOpt.isEmpty() || !"ACTIVE".equals(planOpt.get().getStatus())) {
            return new EnrollmentEligibilityResponse(
                    "INELIGIBLE",
                    "Coverage plan is not active or does not exist for this tenant",
                    planId,
                    null,
                    clientId,
                    false);
        }

        CoveragePlanEntity plan = planOpt.get();
        List<MemberCoverageEntity> existing =
                memberCoverageRepository.findByTenantIdAndClientIdAndStatus(tenantId, clientId, "ACTIVE");
        LocalDate today = LocalDate.now();
        boolean alreadyEnrolled = existing.stream()
                .anyMatch(m -> m.getPlanId().equals(planId) && coversOn(m, today));

        if (alreadyEnrolled) {
            return new EnrollmentEligibilityResponse(
                    "INELIGIBLE",
                    "Member already has active coverage on this plan",
                    planId,
                    plan.getPlanCode(),
                    clientId,
                    false);
        }

        return new EnrollmentEligibilityResponse(
                "ELIGIBLE",
                "Member is eligible to enroll on the selected plan",
                planId,
                plan.getPlanCode(),
                clientId,
                true);
    }

    private static boolean coversOn(MemberCoverageEntity m, LocalDate d) {
        if (m.getEffectiveFrom() != null && d.isBefore(m.getEffectiveFrom())) {
            return false;
        }
        return m.getEffectiveTo() == null || !d.isAfter(m.getEffectiveTo());
    }
}
