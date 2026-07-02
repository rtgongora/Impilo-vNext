package zw.gov.mohcc.impilo.experience.admingovernance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Approving an onboarding access request must stage the account (Keycloak +
 * activation invitation), not merely flip the record to APPROVED.
 */
@ExtendWith(MockitoExtension.class)
class AdminGovernanceOnboardingFulfilmentTest {

    @Mock private SessionExperienceService sessionExperienceService;
    @Mock private AdminGovernancePolicyService policyService;
    @Mock private AdminGovernanceAccessRequestPersistence accessRequestPersistence;
    @Mock private AdminGovernanceAccessRequestStore accessRequestStore;
    @Mock private WorkforceGovernanceClient workforceGovernanceClient;
    @Mock private VashandiServiceClient vashandiServiceClient;
    @Mock private TshepoAuditServiceClient auditClient;
    @Mock private AdminGovernanceAuditHelper auditHelper;
    @Mock private AdminGovernanceOrganisationService organisationService;
    @Mock private AdminGovernanceLookupService lookupService;
    @Mock private GovernanceInvitationService governanceInvitationService;

    private AdminGovernanceService service;

    private static final String TENANT = "moh-zw";
    private static final String ACTOR = "actor-approver";
    private static final String REQUEST_ID = "ar-001";

    @BeforeEach
    void setUp() {
        service = new AdminGovernanceService(
                sessionExperienceService,
                policyService,
                accessRequestPersistence,
                accessRequestStore,
                workforceGovernanceClient,
                vashandiServiceClient,
                auditClient,
                auditHelper,
                organisationService,
                lookupService,
                governanceInvitationService);

        lenient().when(sessionExperienceService.buildExperienceContract(anyString(), any(), any(), anyBoolean()))
                .thenReturn(Map.of());
        lenient().when(policyService.evaluate(any(), any()))
                .thenReturn(new AdminGovernanceDtos.PolicyDecision(
                        true, "impilo.organisation", "1.6.0", "dec-1", List.of(), List.of()));
        lenient().when(accessRequestPersistence.decide(anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(auditHelper.emit(anyString(), anyString(), any(), any()))
                .thenReturn(new AdminGovernanceResponses.AuditEnvelope("audit-1", "corr-1", "ingested"));
    }

    private AdminGovernanceDtos.AccessRequestRecord onboardingRequest(Map<String, Object> payload) {
        return new AdminGovernanceDtos.AccessRequestRecord(
                REQUEST_ID, "onboarding.provider-worker", "PENDING",
                "requester-1", "Requester", "org-1", "Org One",
                "P-1001", "registered_nurse", null, null, null,
                "MEDIUM", "allowed", List.of(), List.of(),
                "2026-07-01T00:00:00Z", "2026-07-01T00:00:00Z",
                payload, "durable", null, null, null, Boolean.FALSE, null, null);
    }

    @Test
    @DisplayName("Approving an onboarding request stages the account via the invitation seam")
    void approveOnboardingDeliversInvitation() {
        when(accessRequestPersistence.find(TENANT, REQUEST_ID)).thenReturn(Optional.of(onboardingRequest(
                Map.of("officialEmail", "nurse@mohcc.gov.zw", "displayName", "Nurse Example"))));
        when(governanceInvitationService.deliverOrganisationInvitation(
                eq("org-1"), eq("nurse@mohcc.gov.zw"), eq("Nurse Example"),
                eq("registered_nurse"), eq("onboarding_provider-worker")))
                .thenReturn(GovernanceInvitationService.InvitationDeliveryResult.sent(
                        "inv-1", "kc-user-1", "2026-07-09T00:00:00Z", "ingested", "Invitation sent."));

        AdminGovernanceDtos.ActionResponse response = service.decideAccessRequest(
                TENANT, ACTOR, null, false, REQUEST_ID,
                new AdminGovernanceDtos.AccessRequestDecisionRequest("approve", null, null));

        assertThat(response.status()).isEqualTo("completed");
        @SuppressWarnings("unchecked")
        Map<String, Object> fulfilment = (Map<String, Object>) response.payload().get("fulfilment");
        assertThat(fulfilment).isNotNull();
        assertThat(fulfilment.get("fulfilmentStatus")).isEqualTo("invitation_sent");
        assertThat(fulfilment.get("invitationId")).isEqualTo("inv-1");
        assertThat(fulfilment.get("keycloakUserId")).isEqualTo("kc-user-1");
        verify(auditHelper).emit(eq("onboarding.fulfilment_invitation_sent"), eq(ACTOR), eq(REQUEST_ID), any());
    }

    @Test
    @DisplayName("Approving without a contact email blocks fulfilment honestly instead of pretending")
    void approveOnboardingWithoutEmailIsBlockedHonestly() {
        when(accessRequestPersistence.find(TENANT, REQUEST_ID)).thenReturn(Optional.of(onboardingRequest(Map.of())));

        AdminGovernanceDtos.ActionResponse response = service.decideAccessRequest(
                TENANT, ACTOR, null, false, REQUEST_ID,
                new AdminGovernanceDtos.AccessRequestDecisionRequest("approve", null, null));

        assertThat(response.status()).isEqualTo("completed");
        @SuppressWarnings("unchecked")
        Map<String, Object> fulfilment = (Map<String, Object>) response.payload().get("fulfilment");
        assertThat(fulfilment).isNotNull();
        assertThat(fulfilment.get("fulfilmentStatus")).isEqualTo("blocked_missing_contact");
        verify(governanceInvitationService, never()).deliverOrganisationInvitation(any(), any(), any(), any(), any());
        verify(auditHelper).emit(eq("onboarding.fulfilment_blocked"), eq(ACTOR), eq(REQUEST_ID), any());
    }

    @Test
    @DisplayName("Rejecting an onboarding request never stages an account")
    void rejectOnboardingDoesNotDeliverInvitation() {
        when(accessRequestPersistence.find(TENANT, REQUEST_ID)).thenReturn(Optional.of(onboardingRequest(
                Map.of("officialEmail", "nurse@mohcc.gov.zw"))));

        AdminGovernanceDtos.ActionResponse response = service.decideAccessRequest(
                TENANT, ACTOR, null, false, REQUEST_ID,
                new AdminGovernanceDtos.AccessRequestDecisionRequest("reject", "not eligible", null));

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.payload().get("fulfilment")).isNull();
        verify(governanceInvitationService, never()).deliverOrganisationInvitation(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Keycloak-unavailable fulfilment surfaces pending_backend, not success")
    void keycloakUnavailableSurfacesPendingBackend() {
        when(accessRequestPersistence.find(TENANT, REQUEST_ID)).thenReturn(Optional.of(onboardingRequest(
                Map.of("officialEmail", "nurse@mohcc.gov.zw", "displayName", "Nurse Example"))));
        when(governanceInvitationService.deliverOrganisationInvitation(any(), any(), any(), any(), any()))
                .thenReturn(GovernanceInvitationService.InvitationDeliveryResult.queued(
                        "inv-2", null, "pending_backend",
                        "Keycloak invitation pending — reconciliation required."));

        AdminGovernanceDtos.ActionResponse response = service.decideAccessRequest(
                TENANT, ACTOR, null, false, REQUEST_ID,
                new AdminGovernanceDtos.AccessRequestDecisionRequest("approve", null, null));

        @SuppressWarnings("unchecked")
        Map<String, Object> fulfilment = (Map<String, Object>) response.payload().get("fulfilment");
        assertThat(fulfilment.get("fulfilmentStatus")).isEqualTo("invitations_pending");
        assertThat(fulfilment.get("integrationStatus")).isEqualTo("pending_backend");
        assertThat(response.integrationStatus()).isEqualTo("pending_backend");
    }
}
