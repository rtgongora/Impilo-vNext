package zw.gov.mohcc.impilo.varapi.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;
import zw.gov.mohcc.impilo.varapi.integration.CouncilRegulatoryPolicyClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.FundoCpdCandidateEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CpdCycleRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.FundoCpdCandidateRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

/**
 * The provider-public-id lookup is the seam Fundo learner surfaces use to show
 * council verification state. It must resolve through the registry public id,
 * never cross tenants, and fail loudly for unknown providers.
 */
class FundoCpdGovernancePublicIdLookupTest {

    private static final UUID TENANT = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID OTHER_TENANT = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    private FundoCpdCandidateRepository candidateRepository;
    private ProviderRepository providerRepository;
    private FundoCpdGovernanceService service;

    @BeforeEach
    void setUp() {
        candidateRepository = mock(FundoCpdCandidateRepository.class);
        providerRepository = mock(ProviderRepository.class);
        service = new FundoCpdGovernanceService(
                candidateRepository,
                providerRepository,
                mock(CouncilRepository.class),
                mock(CpdService.class),
                mock(CpdCycleRepository.class),
                mock(EventOutboxRepository.class),
                new VarapiProperties(),
                mock(CouncilRegulatoryPolicyClient.class));
        TrustContextHolder.set(new TrustContext(
                TENANT, "council-staff-1", "STAFF", "REGULATORY_REVIEW", "device",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    @DisplayName("Resolves candidates through the provider public id within the caller tenant")
    void resolvesThroughPublicId() {
        ProviderEntity provider = new ProviderEntity();
        provider.setId(42L);
        provider.setTenantId(TENANT);
        when(providerRepository.findByProviderPublicId("PRV-PUB-1")).thenReturn(Optional.of(provider));
        FundoCpdCandidateEntity candidate = new FundoCpdCandidateEntity();
        when(candidateRepository.findByTenantIdAndProvider_IdOrderByCreatedAtDesc(eq(TENANT), eq(42L)))
                .thenReturn(List.of(candidate));

        List<FundoCpdCandidateEntity> out = service.listForProviderPublicId("PRV-PUB-1");

        assertThat(out).containsExactly(candidate);
    }

    @Test
    @DisplayName("Rejects a public id whose provider belongs to another tenant")
    void rejectsCrossTenant() {
        ProviderEntity provider = new ProviderEntity();
        provider.setId(43L);
        provider.setTenantId(OTHER_TENANT);
        when(providerRepository.findByProviderPublicId("PRV-PUB-X")).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> service.listForProviderPublicId("PRV-PUB-X"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects an unknown public id")
    void rejectsUnknown() {
        when(providerRepository.findByProviderPublicId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listForProviderPublicId("NOPE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
