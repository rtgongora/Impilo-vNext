package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.integration.FundoIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.IntegrationCheckResult;
import zw.gov.mohcc.impilo.vashandi.integration.TusoIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.VarapiIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.WorkforceGovernanceIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.TrainingRequirementEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.LeaveAvailabilityRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.TrainingRequirementRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The graduated Fundo training-gate enforcement (PO-20260629-01 / G-FU-02):
 * BLOCK denies, CONDITIONAL withholds pending a governance override, ADVISE
 * allows with an audited warning, no requirements → no gate call at all.
 */
@ExtendWith(MockitoExtension.class)
class WorkforceEligibilityTrainingGateTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock WorkforceProfileRepository profileRepository;
    @Mock WorkforceAssignmentRepository assignmentRepository;
    @Mock LeaveAvailabilityRepository leaveRepository;
    @Mock VarapiIntegrationClient varapiClient;
    @Mock WorkforceGovernanceIntegrationClient governanceClient;
    @Mock TusoIntegrationClient tusoClient;
    @Mock FundoIntegrationClient fundoClient;
    @Mock TrainingRequirementRepository trainingRequirementRepository;

    WorkforceEligibilityService service;
    WorkforceAssignmentEntity assignment;
    WorkforceProfileEntity profile;

    @BeforeEach
    void setUp() {
        service = new WorkforceEligibilityService(
                profileRepository, assignmentRepository, leaveRepository,
                varapiClient, governanceClient, tusoClient, fundoClient,
                trainingRequirementRepository);

        profile = new WorkforceProfileEntity();
        profile.setId(UUID.randomUUID());
        profile.setProviderWorkerId("PRV-1");
        profile.setHealthId("HID-1");
        profile.setCurrentStatus("active");

        assignment = new WorkforceAssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setTenantId(TENANT);
        assignment.setWorkforceProfileId(profile.getId());
        assignment.setRoleTemplateId("role-nurse");

        when(profileRepository.findByTenantIdAndId(TENANT, assignment.getWorkforceProfileId()))
                .thenReturn(Optional.of(profile));
        when(varapiClient.fetchProfessionalStatus(anyString()))
                .thenReturn(IntegrationCheckResult.live("varapi", Map.of()));
        when(governanceClient.fetchHscEmploymentSummary(anyString()))
                .thenReturn(IntegrationCheckResult.live("workforce-governance", Map.of()));
        when(fundoClient.fetchTrainingEvidence(anyString()))
                .thenReturn(IntegrationCheckResult.live("fundo", Map.of()));
        when(leaveRepository
                .findByTenantIdAndWorkforceProfileIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(assignmentRepository.findByTenantIdAndWorkforceProfileIdAndStatusIn(any(), any(), any()))
                .thenReturn(List.of());
    }

    private void gateReturns(String decision, List<String> blocking) {
        when(trainingRequirementRepository.findByTenantIdAndRoleTemplateIdAndActiveTrue(TENANT, "role-nurse"))
                .thenReturn(List.of(requirement("INFECT-CTL", "HARD")));
        when(fundoClient.fetchTrainingGate(eq("PRV-1"), any()))
                .thenReturn(IntegrationCheckResult.live("fundo-training-gate",
                        Map.of("decision", decision, "blocking", blocking)));
    }

    private static TrainingRequirementEntity requirement(String code, String level) {
        TrainingRequirementEntity r = new TrainingRequirementEntity();
        r.setTenantId(TENANT);
        r.setRoleTemplateId("role-nurse");
        r.setCourseCode(code);
        r.setEnforcementLevel(level);
        r.setActive(true);
        return r;
    }

    @Test
    @DisplayName("BLOCK decision denies eligibility and names the blocking course")
    void blockDenies() {
        gateReturns("BLOCK", List.of("INFECT-CTL"));
        VashandiDtos.WorkforceEligibilityResult result = service.evaluate(assignment, "opa-1");
        assertThat(result.overallStatus()).isEqualTo("denied");
        assertThat(result.message()).contains("INFECT-CTL");
    }

    @Test
    @DisplayName("CONDITIONAL decision withholds activation pending governance override")
    void conditionalWithholds() {
        gateReturns("CONDITIONAL", List.of("HAND-HYG"));
        VashandiDtos.WorkforceEligibilityResult result = service.evaluate(assignment, "opa-1");
        assertThat(result.overallStatus()).isEqualTo("conditional_training");
        assertThat(result.message()).contains("HAND-HYG");
    }

    @Test
    @DisplayName("ADVISE decision allows with an audited training advisory")
    void adviseAllowsWithWarning() {
        gateReturns("ADVISE", List.of());
        VashandiDtos.WorkforceEligibilityResult result = service.evaluate(assignment, "opa-1");
        assertThat(result.overallStatus()).isEqualTo("allowed");
        assertThat(result.message()).contains("training advisory");
    }

    @Test
    @DisplayName("ALLOW decision keeps plain eligible outcome")
    void allowIsPlainEligible() {
        gateReturns("ALLOW", List.of());
        VashandiDtos.WorkforceEligibilityResult result = service.evaluate(assignment, "opa-1");
        assertThat(result.overallStatus()).isEqualTo("allowed");
        assertThat(result.message()).isEqualTo("eligible");
    }

    @Test
    @DisplayName("No active requirements for the role — gate is never called")
    void noRequirementsNoGateCall() {
        when(trainingRequirementRepository.findByTenantIdAndRoleTemplateIdAndActiveTrue(TENANT, "role-nurse"))
                .thenReturn(List.of());
        VashandiDtos.WorkforceEligibilityResult result = service.evaluate(assignment, "opa-1");
        assertThat(result.overallStatus()).isEqualTo("allowed");
        verify(fundoClient, never()).fetchTrainingGate(anyString(), any());
    }

    @Test
    @DisplayName("Degraded gate call fails safe to pending_backend, not silent allow")
    void degradedGateFailsSafe() {
        when(trainingRequirementRepository.findByTenantIdAndRoleTemplateIdAndActiveTrue(TENANT, "role-nurse"))
                .thenReturn(List.of(requirement("INFECT-CTL", "HARD")));
        when(fundoClient.fetchTrainingGate(eq("PRV-1"), any()))
                .thenReturn(IntegrationCheckResult.degraded("fundo-training-gate", "connection refused"));
        VashandiDtos.WorkforceEligibilityResult result = service.evaluate(assignment, "opa-1");
        assertThat(result.overallStatus()).isEqualTo("pending_backend");
    }
}
