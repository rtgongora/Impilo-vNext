package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAccessRequestEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderAccessRequestRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HAR W3 guard tests — making an HPA-listed profile CLAIMABLE must not make it an account-takeover
 * primitive, nor an oracle for which registration numbers exist.
 *
 * <p>A council registration number is printed on a certificate hanging on a wall. Anyone who has
 * been in the waiting room can read one. So a claim that matches must still be worth exactly as
 * much as a claim that does not: a request for a human decision.</p>
 */
@ExtendWith(MockitoExtension.class)
class HpaPractitionerClaimServiceTest {

    private static final String REG_NUMBER = "HPA-99001";

    @Mock private ProviderAccessRequestService accessRequestService;
    @Mock private ProviderAccessRequestRepository accessRequestRepository;
    @Mock private ProviderRepository providerRepository;

    private HpaPractitionerClaimService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new HpaPractitionerClaimService(
                accessRequestService, accessRequestRepository, providerRepository);
        TrustContextHolder.set(new TrustContext(
                tenantId, "person-actor", "PERSON", "SELF_SERVICE", "device",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        when(accessRequestService.submit(any())).thenAnswer(inv -> {
            ProviderAccessRequestEntity entity = new ProviderAccessRequestEntity();
            entity.setPublicId("REQ-1");
            entity.setStatus("SUBMITTED");
            return entity;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private static ProviderEntity preloadedHpaProvider() {
        ProviderEntity provider = new ProviderEntity();
        provider.setId(7L);
        provider.setProviderPublicId("PROV-ABC");
        provider.setPracticeNumber(REG_NUMBER);
        provider.setLifecycleStatus("PRELOADED");
        provider.setBootstrapOrigin("COUNCIL_IMPORT");
        return provider;
    }

    /** A hit and a miss must be indistinguishable to the caller. */
    @Test
    void matchAndMissReturnTheSameAcknowledgement() {
        when(providerRepository.findFirstByTenantIdAndPracticeNumberIgnoreCase(tenantId, REG_NUMBER))
                .thenReturn(Optional.of(preloadedHpaProvider()));
        var hit = service.claimByRegistrationNumber(REG_NUMBER, "HPA");

        when(providerRepository.findFirstByTenantIdAndPracticeNumberIgnoreCase(tenantId, "NOPE-1"))
                .thenReturn(Optional.empty());
        var miss = service.claimByRegistrationNumber("NOPE-1", "HPA");

        assertThat(hit.message()).isEqualTo(miss.message());
        assertThat(hit.status()).isEqualTo(miss.status());
        assertThat(hit.message()).isEqualTo(HpaPractitionerClaimService.UNIFORM_ACKNOWLEDGEMENT);
    }

    /** The claim records a request. It must never write to the provider itself. */
    @Test
    void claimNeverBindsTheProvider() {
        when(providerRepository.findFirstByTenantIdAndPracticeNumberIgnoreCase(tenantId, REG_NUMBER))
                .thenReturn(Optional.of(preloadedHpaProvider()));

        service.claimByRegistrationNumber(REG_NUMBER, "HPA");

        verify(providerRepository, never()).save(any());
    }

    /** The reviewer — and only the reviewer — is told the number resolved. */
    @Test
    void theMatchIsDisclosedOnlyToTheReviewer() {
        when(providerRepository.findFirstByTenantIdAndPracticeNumberIgnoreCase(tenantId, REG_NUMBER))
                .thenReturn(Optional.of(preloadedHpaProvider()));

        var ack = service.claimByRegistrationNumber(REG_NUMBER, "HPA");

        var saved = org.mockito.ArgumentCaptor.forClass(ProviderAccessRequestEntity.class);
        verify(accessRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getProviderPublicId()).isEqualTo("PROV-ABC");
        assertThat(saved.getValue().getEvidenceSummary()).contains("not proof of identity");
        // …while the caller's acknowledgement carries no provider reference at all.
        assertThat(ack.message()).doesNotContain("PROV-ABC");
    }

    /** A number belonging to a claimed or self-registered provider is not an HPA-listing claim. */
    @Test
    void onlyUnclaimedHpaImportedProfilesAreTreatedAsAMatch() {
        ProviderEntity alreadyClaimed = preloadedHpaProvider();
        alreadyClaimed.setLifecycleStatus("CLAIMED");
        when(providerRepository.findFirstByTenantIdAndPracticeNumberIgnoreCase(tenantId, REG_NUMBER))
                .thenReturn(Optional.of(alreadyClaimed));

        service.claimByRegistrationNumber(REG_NUMBER, "HPA");

        var saved = org.mockito.ArgumentCaptor.forClass(ProviderAccessRequestEntity.class);
        verify(accessRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getProviderPublicId()).isNull();
    }
}
