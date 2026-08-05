package zw.gov.mohcc.impilo.orgregistry.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ProcessingRoleAssignmentEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ServiceAgreementEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ServiceResponsibilityProfileEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.ProcessingRoleAssignmentRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.ServiceAgreementRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.ServiceResponsibilityProfileRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Proof for the §3A.4 controller-resolution order and, more importantly, for what happens
 * when it finds nothing.
 *
 * <p>The MoHCC hospital controllership question (§26.2 #1) is an open legal determination.
 * These tests exist to prove the code refuses rather than guesses — and that it refuses
 * only the operations that genuinely need a controller.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ControllerResolutionServiceTest {

    @Mock private ProcessingRoleAssignmentRepository assignments;
    @Mock private ServiceResponsibilityProfileRepository profiles;
    @Mock private ServiceAgreementRepository agreements;

    private ControllerResolutionService service;

    private final UUID facilityId = UUID.randomUUID();
    private final UUID organisationId = UUID.randomUUID();
    private final UUID trustDomainId = UUID.randomUUID();
    private final UUID agreementId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now();

    @BeforeEach
    void setUp() {
        service = new ControllerResolutionService(assignments, profiles, agreements);
        // Default: nothing assigned anywhere.
        when(assignments.findActiveFor(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    private ControllerResolutionService.ScopeChain fullChain() {
        return new ControllerResolutionService.ScopeChain(
                facilityId, null, agreementId, null, organisationId, trustDomainId);
    }

    private ProcessingRoleAssignmentEntity assignment(String scopeType, UUID scopeId,
                                                      String roleType, UUID partyId) {
        ProcessingRoleAssignmentEntity a = new ProcessingRoleAssignmentEntity();
        a.setAssignmentId(UUID.randomUUID());
        a.setScopeType(scopeType);
        a.setScopeId(scopeId);
        a.setDataDomain("encounters");
        a.setPurpose("TREATMENT");
        a.setLegalBasis("PUBLIC_TASK");
        a.setRoleType(roleType);
        a.setPartyId(partyId);
        a.setStatus("ACTIVE");
        a.setEffectiveFrom(now.minusDays(1));
        a.setRecordedBy("actor");
        return a;
    }

    @Test
    @DisplayName("Proof 3+4: with nothing assigned and no profile, the controller is UNDETERMINED — not a default")
    void undeterminedWhenNothingAssigned() {
        when(agreements.findById(agreementId)).thenReturn(Optional.empty());

        var result = service.resolveController(fullChain(), "encounters", "TREATMENT", now);

        assertThat(result.resolved()).isFalse();
        assertThat(result.controllerPartyId()).isNull();
        assertThat(result.reasonCode()).isIn("CONTROLLER_UNDETERMINED", "RESPONSIBILITY_PROFILE_INCOMPLETE");
        assertThat(result.requiredGovernanceAction()).isNotBlank();
    }

    @Test
    @DisplayName("Proof 2+7(doctrine): an operator-only profile yields no controller — hosting never decides")
    void hostingDoesNotDetermineController() {
        UUID hostingOperator = UUID.randomUUID();
        ServiceResponsibilityProfileEntity profile = new ServiceResponsibilityProfileEntity();
        profile.setResponsibilityProfileId(UUID.randomUUID());
        profile.setProfileCode("MOHCC-HOSTED");
        profile.setHostingModel("NATIONAL_INFRASTRUCTURE");
        // Every operator role held by one party...
        profile.setInfrastructureOperatorId(hostingOperator);
        profile.setPlatformOperatorId(hostingOperator);
        profile.setApplicationOperatorId(hostingOperator);
        profile.setKeyCustodianId(hostingOperator);
        profile.setDataProcessorId(hostingOperator);
        // ...and still nobody is the controller.
        profile.setDataControllerId(null);
        profile.setControllerResolutionState("UNDETERMINED");
        profile.setStatus("ACTIVE");

        ServiceAgreementEntity agreement = new ServiceAgreementEntity();
        agreement.setServiceAgreementId(agreementId);
        agreement.setStatus("ACTIVE");
        agreement.setResponsibilityProfileId(profile.getResponsibilityProfileId());
        when(agreements.findById(agreementId)).thenReturn(Optional.of(agreement));
        when(profiles.findById(profile.getResponsibilityProfileId())).thenReturn(Optional.of(profile));

        var result = service.resolveController(fullChain(), "encounters", "TREATMENT", now);

        assertThat(result.resolved()).isFalse();
        assertThat(result.controllerPartyId())
                .as("the hosting operator must never be promoted to controller")
                .isNotEqualTo(hostingOperator)
                .isNull();
        assertThat(result.reasonCode()).isEqualTo("CONTROLLER_UNDETERMINED");
    }

    @Test
    @DisplayName("Proof 8: resolution is deterministic — the most specific scope wins")
    void mostSpecificScopeWins() {
        UUID facilityParty = UUID.randomUUID();
        UUID orgParty = UUID.randomUUID();
        when(assignments.findActiveFor(eq("FACILITY"), eq(facilityId), any(), any(),
                eq("LEGAL_CONTROLLER"), any()))
                .thenReturn(List.of(assignment("FACILITY", facilityId, "LEGAL_CONTROLLER", facilityParty)));
        when(assignments.findActiveFor(eq("ORGANISATION"), eq(organisationId), any(), any(),
                eq("LEGAL_CONTROLLER"), any()))
                .thenReturn(List.of(assignment("ORGANISATION", organisationId, "LEGAL_CONTROLLER", orgParty)));

        var result = service.resolveController(fullChain(), "encounters", "TREATMENT", now);

        assertThat(result.resolved()).isTrue();
        assertThat(result.controllerPartyId()).isEqualTo(facilityParty);
        assertThat(result.resolvedAtScopeType()).isEqualTo("FACILITY");
    }

    @Test
    @DisplayName("Joint control resolves to the arrangement, never to one arbitrary party")
    void jointControlResolvesToAllParties() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(assignments.findActiveFor(eq("FACILITY"), eq(facilityId), any(), any(),
                eq("JOINT_CONTROLLER"), any()))
                .thenReturn(List.of(
                        assignment("FACILITY", facilityId, "JOINT_CONTROLLER", a),
                        assignment("FACILITY", facilityId, "JOINT_CONTROLLER", b)));

        var result = service.resolveController(fullChain(), "encounters", "TREATMENT", now);

        assertThat(result.resolved()).isTrue();
        assertThat(result.roleType()).isEqualTo("JOINT_CONTROLLER");
        assertThat(result.jointControllerPartyIds()).containsExactlyInAnyOrder(a, b);
        assertThat(result.controllerPartyId())
                .as("joint control must not collapse to a single party")
                .isNull();
    }

    @Test
    @DisplayName("Proof 9: two active controllers at one scope is surfaced, never silently picked from")
    void contradictoryActiveControllersRefuse() {
        when(assignments.findActiveFor(eq("FACILITY"), eq(facilityId), any(), any(),
                eq("LEGAL_CONTROLLER"), any()))
                .thenReturn(List.of(
                        assignment("FACILITY", facilityId, "LEGAL_CONTROLLER", UUID.randomUUID()),
                        assignment("FACILITY", facilityId, "LEGAL_CONTROLLER", UUID.randomUUID())));

        var result = service.resolveController(fullChain(), "encounters", "TREATMENT", now);

        assertThat(result.resolved()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("CONTROLLER_UNDETERMINED");
        assertThat(result.reasonDetail()).contains("More than one active legal controller");
    }

    @Test
    @DisplayName("Proof 17: a later legal determination lands as DATA — one assignment row flips the outcome")
    void laterDeterminationIsDataNotMigration() {
        var before = service.resolveController(fullChain(), "encounters", "TREATMENT", now);
        assertThat(before.resolved()).isFalse();

        // The determination is made. No schema change; a row appears.
        UUID determinedController = UUID.randomUUID();
        when(assignments.findActiveFor(eq("FACILITY"), eq(facilityId), any(), any(),
                eq("LEGAL_CONTROLLER"), any()))
                .thenReturn(List.of(assignment("FACILITY", facilityId, "LEGAL_CONTROLLER", determinedController)));

        var after = service.resolveController(fullChain(), "encounters", "TREATMENT", now);
        assertThat(after.resolved()).isTrue();
        assertThat(after.controllerPartyId()).isEqualTo(determinedController);
    }

    @Test
    @DisplayName("A different data domain does not inherit another domain's controller")
    void controllerDoesNotLeakAcrossDataDomains() {
        when(assignments.findActiveFor(any(), any(), eq("encounters"), any(),
                eq("LEGAL_CONTROLLER"), any()))
                .thenReturn(List.of(assignment("FACILITY", facilityId, "LEGAL_CONTROLLER", UUID.randomUUID())));
        when(agreements.findById(agreementId)).thenReturn(Optional.empty());

        var phr = service.resolveController(fullChain(), "phr", "TREATMENT", now);

        assertThat(phr.resolved())
                .as("a controller for encounters says nothing about the PHR")
                .isFalse();
    }
}
