package zw.gov.mohcc.impilo.daidzai.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.daidzai.integration.CredentialAttestationClient;
import zw.gov.mohcc.impilo.daidzai.integration.MusheContributionClient;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AssistanceRequestEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EventOutboxRepository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AssistanceServiceTest {

    @Autowired private AssistanceService service;
    @Autowired private EventOutboxRepository outboxRepo;
    @MockBean private MusheContributionClient mushe;
    @MockBean private CredentialAttestationClient attestations;

    private AssistanceRequestEntity draft(UUID tenant, String requester) {
        when(mushe.createCampaignRequest(any(), any(), any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new MusheContributionClient.CampaignMoneyLink(
                        UUID.randomUUID(), "tok-" + UUID.randomUUID().toString().substring(0, 8)));
        return service.create(tenant, "CITIZEN", requester, "ANONYMOUS", null,
                "MEDICAL_COSTS", "Help with surgery costs", "A long story",
                new BigDecimal("2500.00"), "USD", null, UUID.randomUUID());
    }

    @Test
    void creation_mints_money_backbone_and_starts_draft() {
        UUID tenant = UUID.randomUUID();
        AssistanceRequestEntity a = draft(tenant, "citizen-1");

        assertThat(a.getVerificationStatus()).isEqualTo("DRAFT");
        assertThat(a.getAssistanceReference()).startsWith("HELP-");
        assertThat(a.getContributionRequestId()).isNotNull();
        assertThat(a.getShareToken()).startsWith("tok-");
        assertThat(outboxRepo.findAll())
                .anyMatch(e -> e.getEventType().equals("daidzai.assistance.created"));
        // Drafts are never publicly listed.
        assertThat(service.listPublic(tenant)).isEmpty();
        assertThat(service.listMine(tenant, "citizen-1")).hasSize(1);
    }

    @Test
    void verified_active_is_reachable_only_through_provider_attestation() {
        UUID tenant = UUID.randomUUID();
        AssistanceRequestEntity a = draft(tenant, "citizen-1");

        // Attesting a DRAFT (not yet verification-requested) is a state error.
        assertThatThrownBy(() -> service.attest(tenant, a.getId(), "prov-1", "PROVIDER", "FAC-1"))
                .isInstanceOf(IllegalStateException.class);

        // Verification request requires consent.
        assertThatThrownBy(() -> service.requestVerification(tenant, a.getId(), "citizen-1", null))
                .isInstanceOf(IllegalArgumentException.class);
        service.requestVerification(tenant, a.getId(), "citizen-1", "consent-123");

        // Non-provider actors cannot attest.
        assertThatThrownBy(() -> service.attest(tenant, a.getId(), "citizen-1", "CITIZEN", "FAC-1"))
                .isInstanceOf(SecurityException.class);

        when(attestations.issueCaseAttestation(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new CredentialAttestationClient.CaseAttestation(
                        "cred-1", "https://verify/token-1"));
        AssistanceRequestEntity verified = service.attest(tenant, a.getId(), "prov-1", "PROVIDER", "FAC-1");

        assertThat(verified.getVerificationStatus()).isEqualTo("ACTIVE");
        assertThat(verified.getAttestationCredentialId()).isEqualTo("cred-1");
        assertThat(verified.getAttestationPublicToken()).isEqualTo("https://verify/token-1");
        assertThat(service.listPublic(tenant)).hasSize(1);
        assertThat(outboxRepo.findAll())
                .anyMatch(e -> e.getEventType().equals("daidzai.assistance.verified"));
    }

    @Test
    void contribution_projection_is_idempotent_and_fulfils_at_target() {
        UUID tenant = UUID.randomUUID();
        AssistanceRequestEntity a = draft(tenant, "citizen-1");
        service.requestVerification(tenant, a.getId(), "citizen-1", "consent-1");
        when(attestations.issueCaseAttestation(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new CredentialAttestationClient.CaseAttestation("cred-1", "url"));
        service.attest(tenant, a.getId(), "prov-1", "PROVIDER", "FAC-1");

        UUID requestId = a.getContributionRequestId();
        UUID c1 = UUID.randomUUID();
        service.applyContribution(requestId, c1, new BigDecimal("1000.00"), false, "Tino", "Get well");
        service.applyContribution(requestId, c1, new BigDecimal("1000.00"), false, "Tino", "Get well"); // replay

        AssistanceRequestEntity after = service.require(tenant, a.getId());
        assertThat(after.getRaisedAmount()).isEqualByComparingTo("1000.00");
        assertThat(after.getDonorCount()).isEqualTo(1);

        // Anonymous donor label is never stored.
        UUID c2 = UUID.randomUUID();
        service.applyContribution(requestId, c2, new BigDecimal("1500.00"), true, "ShouldNotAppear", null);
        assertThat(service.contributionsOf(tenant, a.getId()))
                .filteredOn(c -> c.getContributionId().equals(c2))
                .allMatch(c -> c.getContributorLabel() == null && c.getIsAnonymous());

        // Target reached -> FULFILLED + event.
        AssistanceRequestEntity fulfilled = service.require(tenant, a.getId());
        assertThat(fulfilled.getVerificationStatus()).isEqualTo("FULFILLED");
        assertThat(outboxRepo.findAll())
                .anyMatch(e -> e.getEventType().equals("daidzai.assistance.fulfilled"));
    }

    @Test
    void cancel_refunds_via_mushe_and_only_requester_may_cancel() {
        UUID tenant = UUID.randomUUID();
        AssistanceRequestEntity a = draft(tenant, "citizen-1");

        assertThatThrownBy(() -> service.cancel(tenant, a.getId(), "someone-else"))
                .isInstanceOf(SecurityException.class);

        AssistanceRequestEntity cancelled = service.cancel(tenant, a.getId(), "citizen-1");
        assertThat(cancelled.getVerificationStatus()).isEqualTo("CANCELLED");
        verify(mushe, times(1)).cancelAndRefund(tenant, a.getContributionRequestId());
    }

    @Test
    void release_requires_verified_case() {
        UUID tenant = UUID.randomUUID();
        AssistanceRequestEntity a = draft(tenant, "citizen-1");
        assertThatThrownBy(() -> service.release(tenant, a.getId(), "citizen-1", null))
                .isInstanceOf(IllegalStateException.class);
    }
}
