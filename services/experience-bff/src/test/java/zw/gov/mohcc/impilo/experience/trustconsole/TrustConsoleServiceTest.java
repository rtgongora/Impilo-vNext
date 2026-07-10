package zw.gov.mohcc.impilo.experience.trustconsole;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceAuditHelper;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceDtos;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernancePolicyService;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceService;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoFacilityClaimClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure-Mockito coverage for the Trust Console composition, matching
 * {@link zw.gov.mohcc.impilo.experience.controller.FacilityClaimControllerTest} conventions:
 * mocked downstream clients, degraded-downstream = honest partial (never a throw),
 * decision passthrough verified against the owning system of record.
 */
@ExtendWith(MockitoExtension.class)
class TrustConsoleServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT = "tenant-1";
    private static final String ACTOR = "11111111-2222-3333-4444-555555555555";

    @Mock private SessionExperienceService sessionExperienceService;
    @Mock private AdminGovernancePolicyService policyService;
    @Mock private AdminGovernanceAuditHelper auditHelper;
    @Mock private AdminGovernanceService adminGovernanceService;
    @Mock private VarapiServiceClient varapiClient;
    @Mock private TusoFacilityClaimClient tusoClient;
    @Mock private IdentityAssuranceServiceClient identityAssuranceClient;
    @Mock private zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceImportService importService;

    private TrustConsoleService service;

    @BeforeEach
    void setUp() {
        service = new TrustConsoleService(sessionExperienceService, policyService, auditHelper,
                adminGovernanceService, varapiClient, tusoClient, identityAssuranceClient, importService);
        lenient().when(importService.list(any())).thenReturn(
                new AdminGovernanceDtos.LookupEnvelope("live", null, Map.of("items", List.of())));
        lenient().when(sessionExperienceService.buildExperienceContract(anyString(), any(), any(), anyBoolean()))
                .thenReturn(Map.of("visibleManagementWorkspaces", List.of("national_platform_user_administration")));
        lenient().when(policyService.evaluate(any(), any())).thenReturn(allowed());
    }

    private static AdminGovernanceDtos.PolicyDecision allowed() {
        return new AdminGovernanceDtos.PolicyDecision(true, "impilo.organisation", "1.6.0",
                "decision-1", List.of(), List.of());
    }

    private static AdminGovernanceDtos.PolicyDecision deniedDecision() {
        return new AdminGovernanceDtos.PolicyDecision(false, "impilo.organisation", "1.6.0",
                "decision-2", List.of("No management authority in current session"), List.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> summary, String queue) {
        return (Map<String, Object>) ((Map<String, Object>) summary.get("queues")).get(queue);
    }

    // ── Summary ──────────────────────────────────────────────────────────────────

    @Test
    void summary_countsEveryQueueWhenAllDownstreamsLive() throws Exception {
        when(varapiClient.listProviderAccessRequestsForReview(null))
                .thenReturn(MAPPER.readTree("[{\"publicId\":\"PAR-1\"},{\"publicId\":\"PAR-2\"}]"));
        when(tusoClient.appointmentsByState("PENDING"))
                .thenReturn(MAPPER.readTree("[{\"id\":7}]"));
        when(adminGovernanceService.listAccessRequests(TENANT, ACTOR))
                .thenReturn(List.of(orgRequest("r1", "PENDING"), orgRequest("r2", "APPROVED")));
        when(identityAssuranceClient.listUpgradeRequests())
                .thenReturn(MAPPER.readTree("[{\"id\":1},{\"id\":2},{\"id\":3}]"));

        Map<String, Object> summary = service.summary(TENANT, ACTOR, null, false);

        assertEquals("live", summary.get("integrationStatus"));
        assertEquals(2, section(summary, "provider-access-requests").get("pendingCount"));
        assertEquals(1, section(summary, "facility-admin-appointments").get("pendingCount"));
        // Decided org requests are excluded from the pending count.
        assertEquals(1, section(summary, "org-access-requests").get("pendingCount"));
        assertEquals(3, section(summary, "assurance-upgrades").get("pendingCount"));
    }

    @Test
    void summary_deadDownstreamDegradesThatQueueOnly_never500() throws Exception {
        when(varapiClient.listProviderAccessRequestsForReview(null))
                .thenThrow(new RuntimeException("connection refused"));
        when(tusoClient.appointmentsByState("PENDING")).thenReturn(MAPPER.readTree("[]"));
        when(adminGovernanceService.listAccessRequests(TENANT, ACTOR)).thenReturn(List.of());
        when(identityAssuranceClient.listUpgradeRequests()).thenReturn(MAPPER.readTree("[]"));

        Map<String, Object> summary = service.summary(TENANT, ACTOR, null, false);

        assertEquals("partial", summary.get("integrationStatus"));
        Map<String, Object> degraded = section(summary, "provider-access-requests");
        assertEquals("pending_backend", degraded.get("integrationStatus"));
        assertNull(degraded.get("pendingCount"));
        assertNotNull(degraded.get("friendlyMessage"));
        // The other queues stay live with honest zero counts.
        assertEquals("live", section(summary, "facility-admin-appointments").get("integrationStatus"));
        assertEquals(0, section(summary, "facility-admin-appointments").get("pendingCount"));
    }

    @Test
    void summary_deniedByPolicyNeverTouchesDownstreams() {
        when(policyService.evaluate(any(), any())).thenReturn(deniedDecision());

        Map<String, Object> summary = service.summary(TENANT, ACTOR, null, false);

        assertEquals("denied", summary.get("status"));
        verifyNoInteractions(varapiClient, tusoClient, identityAssuranceClient);
    }

    // ── Queues ───────────────────────────────────────────────────────────────────

    @Test
    void queue_providerAccessRequests_returnsItems() throws Exception {
        when(varapiClient.listProviderAccessRequestsForReview(null))
                .thenReturn(MAPPER.readTree("[{\"publicId\":\"PAR-1\",\"status\":\"PENDING_NATIONAL_REVIEW\"}]"));

        Map<String, Object> queue = service.queue(TENANT, ACTOR, null, false, "provider-access-requests");

        assertEquals("live", queue.get("integrationStatus"));
        assertEquals(1, queue.get("count"));
        assertEquals("provider-access-requests", queue.get("queue"));
    }

    @Test
    void queue_unknownNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.queue(TENANT, ACTOR, null, false, "everything"));
    }

    @Test
    void queue_degradedDownstreamIsHonestEmptyWithPendingBackend() {
        when(identityAssuranceClient.listUpgradeRequests()).thenThrow(new RuntimeException("boom"));

        Map<String, Object> queue = service.queue(TENANT, ACTOR, null, false, "assurance-upgrades");

        assertEquals("pending_backend", queue.get("integrationStatus"));
        assertEquals(List.of(), queue.get("items"));
        assertNull(queue.get("count"));
    }

    // ── Decisions ────────────────────────────────────────────────────────────────

    @Test
    void decide_providerAccess_passesDecisionAndNoteToVarapi() throws Exception {
        when(varapiClient.decideProviderAccessRequest(eq("PAR-9"), any()))
                .thenReturn(MAPPER.readTree("{\"publicId\":\"PAR-9\",\"status\":\"APPROVED\"}"));

        Map<String, Object> out = service.decide(TENANT, ACTOR, null, false,
                "provider-access-requests", "PAR-9", "approved", "Council confirmed");

        assertEquals("completed", out.get("status"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(varapiClient).decideProviderAccessRequest(eq("PAR-9"), captor.capture());
        assertEquals("APPROVED", captor.getValue().get("decision"));
        assertEquals("Council confirmed", captor.getValue().get("note"));
    }

    @Test
    void decide_facilityAdmin_approveBindsApproverToSessionActor() throws Exception {
        when(tusoClient.approve(eq("7"), any()))
                .thenReturn(MAPPER.readTree("{\"id\":7,\"approvalState\":\"ACTIVE\"}"));

        Map<String, Object> out = service.decide(TENANT, ACTOR, null, false,
                "facility-admin-appointments", "7", "APPROVED", null);

        assertEquals("completed", out.get("status"));
        verify(tusoClient).approve("7", Map.of("approvedBy", ACTOR));
    }

    @Test
    void decide_facilityAdmin_rejectIsHonestlyUnsupported() {
        assertThrows(IllegalArgumentException.class, () -> service.decide(
                TENANT, ACTOR, null, false, "facility-admin-appointments", "7", "REJECTED", null));
        verifyNoInteractions(tusoClient);
    }

    @Test
    void decide_orgAccess_reusesAdminGovernanceSeamSoFulfilmentStillFires() {
        AdminGovernanceDtos.ActionResponse response = new AdminGovernanceDtos.ActionResponse(
                "completed", "req-1", null, null, "Access request approved", "ok",
                List.of(), null, allowed(), null, "live", Map.of(), null, null);
        when(adminGovernanceService.decideAccessRequest(
                eq(TENANT), eq(ACTOR), any(), anyBoolean(), eq("req-1"), any()))
                .thenReturn(response);

        Map<String, Object> out = service.decide(TENANT, ACTOR, null, false,
                "org-access-requests", "req-1", "APPROVED", "welcome");

        assertEquals("completed", out.get("status"));
        ArgumentCaptor<AdminGovernanceDtos.AccessRequestDecisionRequest> captor =
                ArgumentCaptor.forClass(AdminGovernanceDtos.AccessRequestDecisionRequest.class);
        verify(adminGovernanceService).decideAccessRequest(
                eq(TENANT), eq(ACTOR), any(), anyBoolean(), eq("req-1"), captor.capture());
        assertEquals("approve", captor.getValue().decision());
        assertEquals("welcome", captor.getValue().notes());
    }

    @Test
    void decide_assurance_mapsApproveBooleanAndNumericId() throws Exception {
        when(identityAssuranceClient.decideUpgradeRequest(eq(5L), any()))
                .thenReturn(MAPPER.readTree("{\"id\":5,\"status\":\"APPROVED\"}"));

        Map<String, Object> out = service.decide(TENANT, ACTOR, null, false,
                "assurance-upgrades", "5", "APPROVED", "verified in person");

        assertEquals("completed", out.get("status"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(identityAssuranceClient).decideUpgradeRequest(eq(5L), captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().get("approve"));
        assertEquals("verified in person", captor.getValue().get("reason"));
    }

    @Test
    void decide_downstreamFailureIsPendingBackendNotAThrow() {
        when(varapiClient.decideProviderAccessRequest(eq("PAR-9"), any()))
                .thenThrow(new RuntimeException("503 from varapi"));

        Map<String, Object> out = service.decide(TENANT, ACTOR, null, false,
                "provider-access-requests", "PAR-9", "APPROVED", null);

        assertEquals("pending_backend", out.get("status"));
        assertNotNull(out.get("friendlyMessage"));
    }

    @Test
    void decide_deniedByPolicyNeverReachesSoR() {
        when(policyService.evaluate(any(), any())).thenReturn(deniedDecision());

        Map<String, Object> out = service.decide(TENANT, ACTOR, null, false,
                "provider-access-requests", "PAR-9", "APPROVED", null);

        assertEquals("denied", out.get("status"));
        verifyNoInteractions(varapiClient);
    }

    private static AdminGovernanceDtos.AccessRequestRecord orgRequest(String id, String status) {
        return new AdminGovernanceDtos.AccessRequestRecord(
                id, "onboarding.provider-worker", status, ACTOR, "Reviewer", null, null,
                "subject-1", "role", null, null, null, "LOW", null, List.of(), List.of(),
                "2026-07-09T00:00:00Z", "2026-07-09T00:00:00Z", Map.of(), "live",
                null, null, null, false, null, null);
    }

    @org.junit.jupiter.api.Test
    void invitationsQueue_listsOnlyActionableRows() {
        when(importService.list(any())).thenReturn(new AdminGovernanceDtos.LookupEnvelope(
                "live", null, Map.of("items", List.of(Map.of("id", "batch-1")))));
        when(importService.listRows("batch-1")).thenReturn(new AdminGovernanceDtos.LookupEnvelope(
                "live", null, Map.of("items", List.of(
                        Map.of("id", "row-1", "invitation", Map.of("status", "sent"),
                                "providerPublicId", "PROV-1"),
                        Map.of("id", "row-2", "invitation", Map.of("status", "activated")),
                        Map.of("id", "row-3", "invitation", Map.of("status", "expired"))))));

        Map<String, Object> section = service.queue("t", "actor", null, false, "pending-invitations");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) section.get("items");
        org.assertj.core.api.Assertions.assertThat(items).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(items.get(0).get("id")).isEqualTo("batch-1:row-1");
        org.assertj.core.api.Assertions.assertThat(items.get(1).get("id")).isEqualTo("batch-1:row-3");
    }

    @org.junit.jupiter.api.Test
    void invitationsQueue_degradesHonestlyWhenWgvDown() {
        when(importService.list(any())).thenReturn(new AdminGovernanceDtos.LookupEnvelope(
                "pending_backend", "Import history unavailable.", null));

        Map<String, Object> section = service.queue("t", "actor", null, false, "pending-invitations");

        org.assertj.core.api.Assertions.assertThat(section.get("integrationStatus"))
                .isEqualTo("pending_backend");
    }

    @org.junit.jupiter.api.Test
    void invitationDecision_resendRoutesToRowAction() {
        when(importService.resendRowInvitation("actor", null, false, "batch-1", "row-1"))
                .thenReturn(new AdminGovernanceDtos.ActionResponse("completed", null, null, null,
                        "Invitation resent", "A fresh activation link was issued.",
                        List.of(), null, null, null, "live", Map.of(), "live", null));

        Map<String, Object> out = service.decide("t", "actor", null, false,
                "pending-invitations", "batch-1:row-1", "RESEND", null);

        org.assertj.core.api.Assertions.assertThat(out.get("status")).isEqualTo("completed");
        org.assertj.core.api.Assertions.assertThat(out.get("title")).isEqualTo("Invitation resent");
    }

    @org.junit.jupiter.api.Test
    void invitationDecision_rejectsUnknownVerbs() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.decide("t", "actor", null, false,
                                "pending-invitations", "batch-1:row-1", "APPROVED", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RESEND or REVOKE");
    }
}
