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
import static org.mockito.Mockito.when;

/**
 * Professional standing was fetched and never read.
 *
 * <p>The VARAPI check counted only as live-or-degraded, so a successful call meant the
 * assignment proceeded whatever the registry said — a suspended or revoked practitioner
 * would be activated onto a facility roster because "the registry answered" was taken for
 * "the registry approved".
 *
 * <p>The gate denies on an affirmatively barring licence, never on an unknown one. That is
 * not timidity: exactly one provider in the estate carries a {@code licence_status} at all,
 * so demanding a positive ACTIVE licence would deny 4,267 of 4,268 and every assignment with
 * them. Unknown passes — and says so.
 */
@ExtendWith(MockitoExtension.class)
class WorkforceEligibilityLicenceGateTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String OPA = "opa-decision-1";

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

        when(profileRepository.findByTenantIdAndId(TENANT, assignment.getWorkforceProfileId()))
                .thenReturn(Optional.of(profile));
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

    private void standingSays(Map<String, Object> payload) {
        when(varapiClient.fetchProfessionalStatus(anyString()))
                .thenReturn(IntegrationCheckResult.live("varapi", payload));
    }

    @Test
    @DisplayName("a suspended licence denies the assignment")
    void suspendedLicenceDenies() {
        standingSays(Map.of("licenceStatus", "SUSPENDED"));

        VashandiDtos.WorkforceEligibilityResult result = service.evaluate(assignment, OPA);

        assertThat(result.overallStatus()).isEqualTo("denied");
        assertThat(result.message()).contains("suspended").contains("bars this provider");
    }

    @Test
    @DisplayName("a revoked licence denies, whichever field carries it")
    void revokedLifecycleDenies() {
        // The bar can arrive as the licence itself or as the registry's disposition of the
        // practitioner. Either one is a bar.
        standingSays(Map.of("lifecycleStatus", "REVOKED"));

        assertThat(service.evaluate(assignment, OPA).overallStatus()).isEqualTo("denied");
    }

    @Test
    @DisplayName("an active licence is eligible without qualification")
    void activeLicenceIsEligible() {
        standingSays(Map.of("licenceStatus", "ACTIVE"));

        VashandiDtos.WorkforceEligibilityResult result = service.evaluate(assignment, OPA);

        assertThat(result.overallStatus()).isEqualTo("allowed");
        assertThat(result.message()).isEqualTo("eligible");
    }

    @Test
    @DisplayName("an unrecorded licence does NOT block — but the result says it is unverified")
    void unknownLicencePassesButIsDeclared() {
        // 4,267 of 4,268 providers are in exactly this state. Denying here would be an outage
        // wearing a safety badge; passing silently would be the defect this gate replaces.
        standingSays(Map.of());

        VashandiDtos.WorkforceEligibilityResult result = service.evaluate(assignment, OPA);

        assertThat(result.overallStatus()).isEqualTo("allowed");
        assertThat(result.message())
                .as("'we do not know' must not read as 'we checked'")
                .contains("not recorded")
                .contains("unverified");
    }

    @Test
    @DisplayName("a restricted licence limits scope, it does not bar practice")
    void restrictedLicenceIsNotABar() {
        // Scope belongs to the privilege and role checks. Treating RESTRICTED as a bar here
        // would deny practitioners who are lawfully practising within narrower limits.
        standingSays(Map.of("licenceStatus", "RESTRICTED"));

        assertThat(service.evaluate(assignment, OPA).overallStatus()).isEqualTo("allowed");
    }
}
