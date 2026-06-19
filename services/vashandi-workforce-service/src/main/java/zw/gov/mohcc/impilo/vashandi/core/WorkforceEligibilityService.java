package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.integration.FundoIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.IntegrationCheckResult;
import zw.gov.mohcc.impilo.vashandi.integration.TusoIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.VarapiIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.WorkforceGovernanceIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.LeaveAvailabilityRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WorkforceEligibilityService {

    private final WorkforceProfileRepository profileRepository;
    private final WorkforceAssignmentRepository assignmentRepository;
    private final LeaveAvailabilityRepository leaveRepository;
    private final VarapiIntegrationClient varapiClient;
    private final WorkforceGovernanceIntegrationClient governanceClient;
    private final TusoIntegrationClient tusoClient;
    private final FundoIntegrationClient fundoClient;

    public WorkforceEligibilityService(WorkforceProfileRepository profileRepository,
                                       WorkforceAssignmentRepository assignmentRepository,
                                       LeaveAvailabilityRepository leaveRepository,
                                       VarapiIntegrationClient varapiClient,
                                       WorkforceGovernanceIntegrationClient governanceClient,
                                       TusoIntegrationClient tusoClient,
                                       FundoIntegrationClient fundoClient) {
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
        this.leaveRepository = leaveRepository;
        this.varapiClient = varapiClient;
        this.governanceClient = governanceClient;
        this.tusoClient = tusoClient;
        this.fundoClient = fundoClient;
    }

    public VashandiDtos.WorkforceEligibilityResult evaluate(WorkforceAssignmentEntity assignment, String opaDecisionId) {
        List<IntegrationCheckResult> checks = new ArrayList<>();
        WorkforceProfileEntity profile = profileRepository.findByTenantIdAndId(
                assignment.getTenantId(), assignment.getWorkforceProfileId()).orElse(null);

        if (profile == null) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "denied", opaDecisionId, checks, "workforce profile missing");
        }

        checks.add(varapiClient.fetchProfessionalStatus(profile.getProviderWorkerId()));
        checks.add(governanceClient.fetchHscEmploymentSummary(profile.getHealthId()));
        checks.add(fundoClient.fetchTrainingEvidence(profile.getProviderWorkerId()));
        if (assignment.getFacilityId() != null) {
            checks.add(tusoClient.validateFacility(assignment.getFacilityId()));
        }

        boolean onLeave = leaveRepository
                .findByTenantIdAndWorkforceProfileIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        assignment.getTenantId(), profile.getId(), "approved", LocalDate.now(), LocalDate.now())
                .stream().findAny().isPresent();
        if (onLeave) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "denied", opaDecisionId, checks, "worker on approved leave");
        }

        List<WorkforceAssignmentEntity> conflicts = assignmentRepository
                .findByTenantIdAndWorkforceProfileIdAndStatusIn(
                        assignment.getTenantId(), profile.getId(), List.of("active", "approved"));
        boolean conflict = conflicts.stream().anyMatch(a -> !a.getId().equals(assignment.getId())
                && assignment.getFacilityId() != null
                && assignment.getFacilityId().equals(a.getFacilityId()));
        if (conflict) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "denied", opaDecisionId, checks, "conflicting active assignment");
        }

        boolean anyDegraded = checks.stream().anyMatch(c -> IntegrationCheckResult.DEGRADED.equals(c.status()));
        if (anyDegraded) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "pending_backend", opaDecisionId, checks, "upstream dependency unavailable");
        }

        if (opaDecisionId == null || opaDecisionId.isBlank()) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "pending", opaDecisionId, checks, "opa decision required");
        }

        if ("suspended".equals(profile.getCurrentStatus()) || "offboarded".equals(profile.getCurrentStatus())) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "denied", opaDecisionId, checks, "worker not eligible");
        }

        return new VashandiDtos.WorkforceEligibilityResult(
                assignment.getId(), "allowed", opaDecisionId, checks, "eligible");
    }
}
