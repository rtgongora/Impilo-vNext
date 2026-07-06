package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AuthorizedRepresentativeEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgClaimSubmissionEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.VerificationStatus;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.AuthorizedRepresentativeRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrgClaimSubmissionRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimSubmissionServiceTest {

    @Mock
    OrgClaimSubmissionRepository claimRepository;
    @Mock
    OrganizationRepository organizationRepository;
    @Mock
    AuthorizedRepresentativeRepository representativeRepository;
    @Mock
    OrgRegistryOutboxWriter outboxWriter;
    @Mock
    WorkflowServiceClient workflowServiceClient;

    ClaimSubmissionService service;

    UUID tenant;

    @BeforeEach
    void setUp() {
        service = new ClaimSubmissionService(
                claimRepository, organizationRepository, representativeRepository,
                outboxWriter, new ObjectMapper(), workflowServiceClient);
        tenant = UUID.randomUUID();
    }

    private OrgClaimSubmissionEntity claimWithStatus(String status) {
        OrgClaimSubmissionEntity claim = new OrgClaimSubmissionEntity();
        claim.setId(UUID.randomUUID());
        claim.setTenantId(tenant);
        claim.setOrganizationId(UUID.randomUUID());
        claim.setSubmittedByRepId(UUID.randomUUID());
        claim.setSubjectHealthId("HID-200");
        claim.setClaimedRole("NURSE");
        claim.setStatus(status);
        when(claimRepository.findById(claim.getId())).thenReturn(Optional.of(claim));
        return claim;
    }

    @Test
    void submit_createsSubmittedClaimWithOrgDelegatedTrustBasis() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID repId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setTenantId(tenant);
        AuthorizedRepresentativeEntity rep = new AuthorizedRepresentativeEntity();
        rep.setId(repId);
        rep.setOrganizationId(orgId);
        rep.setVerificationStatus(VerificationStatus.VERIFIED);

        when(organizationRepository.findByTenantIdAndId(tenant, orgId)).thenReturn(Optional.of(org));
        when(representativeRepository.findByOrganizationIdAndId(orgId, repId)).thenReturn(Optional.of(rep));
        when(claimRepository.save(any(OrgClaimSubmissionEntity.class))).thenAnswer(inv -> {
            OrgClaimSubmissionEntity c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            return c;
        });

        OrgClaimSubmissionEntity claim = service.submit(tenant, orgId,
                new OrgRegistryDtos.CreateClaimRequest(
                        repId, "HID-200", "NURSE", null, Map.of("letter", "doc://evidence/9")));

        assertThat(claim.getStatus()).isEqualTo("SUBMITTED");
        assertThat(claim.getTrustBasis()).isEqualTo("ORG_DELEGATED");
        assertThat(claim.getAdjudicationRef()).isNull();
    }

    @Test
    void submit_rejectsUnverifiedRepresentative() {
        UUID orgId = UUID.randomUUID();
        UUID repId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setTenantId(tenant);
        AuthorizedRepresentativeEntity rep = new AuthorizedRepresentativeEntity();
        rep.setId(repId);
        rep.setOrganizationId(orgId);
        rep.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);

        when(organizationRepository.findByTenantIdAndId(tenant, orgId)).thenReturn(Optional.of(org));
        when(representativeRepository.findByOrganizationIdAndId(orgId, repId)).thenReturn(Optional.of(rep));

        assertThatThrownBy(() -> service.submit(tenant, orgId,
                new OrgRegistryDtos.CreateClaimRequest(repId, "HID-200", "NURSE", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VERIFIED authorized representative");
    }

    @Test
    void transition_submittedToUnderReviewToAccepted() throws Exception {
        when(claimRepository.save(any(OrgClaimSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        OrgClaimSubmissionEntity claim = claimWithStatus("SUBMITTED");
        OrgClaimSubmissionEntity underReview = service.transition(tenant, claim.getId(), "UNDER_REVIEW", null);
        assertThat(underReview.getStatus()).isEqualTo("UNDER_REVIEW");

        OrgClaimSubmissionEntity accepted =
                service.transition(tenant, claim.getId(), "ACCEPTED", "ADJ-2026-001");
        assertThat(accepted.getStatus()).isEqualTo("ACCEPTED");
        assertThat(accepted.getAdjudicationRef()).isEqualTo("ADJ-2026-001");
    }

    @Test
    void transition_submittedCanBeRejectedDirectly() throws Exception {
        when(claimRepository.save(any(OrgClaimSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        OrgClaimSubmissionEntity claim = claimWithStatus("SUBMITTED");
        OrgClaimSubmissionEntity rejected = service.transition(tenant, claim.getId(), "REJECTED", null);
        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void transition_rejectsSubmittedDirectlyToAccepted() {
        OrgClaimSubmissionEntity claim = claimWithStatus("SUBMITTED");

        assertThatThrownBy(() -> service.transition(tenant, claim.getId(), "ACCEPTED", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("illegal claim transition SUBMITTED -> ACCEPTED");
    }

    @Test
    void transition_terminalStatesAreImmutable() {
        OrgClaimSubmissionEntity accepted = claimWithStatus("ACCEPTED");
        assertThatThrownBy(() -> service.transition(tenant, accepted.getId(), "REJECTED", null))
                .isInstanceOf(IllegalStateException.class);

        OrgClaimSubmissionEntity rejected = claimWithStatus("REJECTED");
        assertThatThrownBy(() -> service.transition(tenant, rejected.getId(), "UNDER_REVIEW", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transition_rejectsUnknownStatus() {
        assertThatThrownBy(() -> service.transition(tenant, UUID.randomUUID(), "APPROVED", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown claim status");
    }

    // ── START ARC ────────────────────────────────────────────────────────────

    @Test
    void escalate_startsWorkflowInstanceAndLinksItToClaim() throws Exception {
        when(claimRepository.save(any(OrgClaimSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        OrgClaimSubmissionEntity claim = claimWithStatus("SUBMITTED");
        UUID instanceId = UUID.randomUUID();

        org.mockito.ArgumentCaptor<OrgClaimSubmissionEntity> captor =
                org.mockito.ArgumentCaptor.forClass(OrgClaimSubmissionEntity.class);
        when(workflowServiceClient.startAdjudication(eq(tenant), captor.capture(), eq("actor-1"), isNull()))
                .thenReturn(Optional.of(instanceId));

        OrgClaimSubmissionEntity escalated = service.escalateToAdjudication(tenant, claim.getId(), "actor-1");

        assertThat(escalated.getStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(escalated.getWorkflowInstanceId()).isEqualTo(instanceId);
        // subjectRef of the started workflow is the claim id (threaded via the claim).
        assertThat(captor.getValue().getId()).isEqualTo(claim.getId());
    }

    @Test
    void escalate_workflowStartFailureLeavesClaimHonestPendingNotCorrupted() throws Exception {
        when(claimRepository.save(any(OrgClaimSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        OrgClaimSubmissionEntity claim = claimWithStatus("SUBMITTED");

        when(workflowServiceClient.startAdjudication(eq(tenant), any(), eq("actor-1"), isNull()))
                .thenReturn(Optional.empty());

        OrgClaimSubmissionEntity escalated = service.escalateToAdjudication(tenant, claim.getId(), "actor-1");

        // Honest pending/unavailable: UNDER_REVIEW but no workflow instance linked.
        assertThat(escalated.getStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(escalated.getWorkflowInstanceId()).isNull();
    }

    // ── DECISION-FEEDBACK ARC ────────────────────────────────────────────────

    @Test
    void resolveFromDecision_approvedResolvesClaimToAcceptedWithDecisionId() throws Exception {
        when(claimRepository.save(any(OrgClaimSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID instanceId = UUID.randomUUID();
        OrgClaimSubmissionEntity claim = claimUnderReviewWithInstance(instanceId);

        Optional<OrgClaimSubmissionEntity> resolved = service.resolveFromDecision(
                tenant, instanceId, claim.getId().toString(), "APPROVED", "DEC-777", "corr-1");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getStatus()).isEqualTo("ACCEPTED");
        assertThat(resolved.get().getAdjudicationRef()).isEqualTo("DEC-777");
    }

    @Test
    void resolveFromDecision_deniedResolvesClaimToRejectedWithDecisionId() throws Exception {
        when(claimRepository.save(any(OrgClaimSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID instanceId = UUID.randomUUID();
        OrgClaimSubmissionEntity claim = claimUnderReviewWithInstance(instanceId);

        Optional<OrgClaimSubmissionEntity> resolved = service.resolveFromDecision(
                tenant, instanceId, null, "DENIED", "DEC-888", "corr-2");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getStatus()).isEqualTo("REJECTED");
        assertThat(resolved.get().getAdjudicationRef()).isEqualTo("DEC-888");
    }

    @Test
    void resolveFromDecision_unknownClaimIsDroppedHonestlyNotACrash() throws Exception {
        UUID unknownInstance = UUID.randomUUID();
        UUID unknownSubject = UUID.randomUUID();
        when(claimRepository.findByWorkflowInstanceId(unknownInstance)).thenReturn(Optional.empty());
        when(claimRepository.findById(unknownSubject)).thenReturn(Optional.empty());

        Optional<OrgClaimSubmissionEntity> resolved = service.resolveFromDecision(
                tenant, unknownInstance, unknownSubject.toString(), "APPROVED", "DEC-999", "corr-3");

        assertThat(resolved).isEmpty();
        verify(claimRepository, never()).save(any());
    }

    @Test
    void resolveFromDecision_conditionalIsNotAutoResolved() {
        Optional<OrgClaimSubmissionEntity> resolved = service.resolveFromDecision(
                tenant, UUID.randomUUID(), "irrelevant", "CONDITIONAL", "DEC-COND", "corr-4");

        assertThat(resolved).isEmpty();
        verify(claimRepository, never()).save(any());
    }

    private OrgClaimSubmissionEntity claimUnderReviewWithInstance(UUID instanceId) {
        OrgClaimSubmissionEntity claim = claimWithStatus("UNDER_REVIEW");
        claim.setWorkflowInstanceId(instanceId);
        when(claimRepository.findByWorkflowInstanceId(instanceId)).thenReturn(Optional.of(claim));
        return claim;
    }
}
