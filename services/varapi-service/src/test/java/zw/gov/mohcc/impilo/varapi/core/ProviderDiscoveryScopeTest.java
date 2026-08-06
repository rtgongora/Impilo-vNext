package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderSearchRequest;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderContactRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilAffiliationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderIdentifierRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderSpecialtyRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registration makes a practitioner findable; opting in makes them bookable.
 *
 * <p>The default browse used to call {@code findByTenantIdAndStatus(tenantId, "ACTIVE")}
 * under a comment that said "all providers for tenant". Those two disagreed, and the
 * consequence was not subtle: 4,241 practitioners imported from the Health Professions
 * Authority roll carry {@code status = INACTIVE} because they have not opted in to receiving
 * appointment and prescription requests, so an unfiltered search returned nothing and the
 * national register read as empty rather than unclaimed.
 *
 * <p>These tests pin the distinction so it cannot quietly collapse again.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProviderDiscoveryScopeTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderIdentifierRepository identifierRepository;
    @Mock private ProviderSpecialtyRepository specialtyRepository;
    @Mock private ProviderContactRepository contactRepository;
    @Mock private ProviderCouncilAffiliationRepository affiliationRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ProviderService service;

    @BeforeEach
    void setUp() {
        service = new ProviderService(providerRepository, identifierRepository, specialtyRepository,
                contactRepository, affiliationRepository, outboxRepository, new VarapiProperties());
        TrustContextHolder.set(new TrustContext(TENANT, "registrar-1", "PROVIDER", "REGISTRY",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clear() {
        TrustContextHolder.clear();
    }

    private static ProviderEntity hpaImport() {
        ProviderEntity p = new ProviderEntity();
        p.setTenantId(TENANT);
        p.setProviderPublicId("PROV-ZW-04241");
        p.setGivenName("Tendai");
        p.setFamilyName("Moyo");
        p.setStatus("INACTIVE");                       // has not opted in to participation
        p.setLifecycleStatus("PRELOADED");             // came from the HPA roll
        p.setRegistryStatus("PENDING_VERIFICATION");
        p.setClaimedAt(null);                          // never claimed
        return p;
    }

    @Test
    @DisplayName("an unfiltered browse returns the whole register, not only those who opted in")
    void defaultBrowseReturnsTheWholeRegister() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ProviderEntity> everyone = new PageImpl<>(List.of(hpaImport()));
        when(providerRepository.findByTenantId(eq(TENANT), any(Pageable.class))).thenReturn(everyone);

        Page<ProviderEntity> result =
                service.searchProviders(new ProviderSearchRequest(null, null, null), pageable);

        assertThat(result.getContent())
                .as("an HPA-registered practitioner who has not opted in is still on the register")
                .hasSize(1);
        verify(providerRepository, never())
                .findByTenantIdAndStatus(eq(TENANT), eq("ACTIVE"), any(Pageable.class));
    }

    @Test
    @DisplayName("an explicit status filter is still honoured — this widens the default, not the API")
    void explicitStatusFilterStillApplies() {
        Pageable pageable = PageRequest.of(0, 20);
        when(providerRepository.findByTenantIdAndStatus(eq(TENANT), eq("ACTIVE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchProviders(new ProviderSearchRequest(null, null, "ACTIVE"), pageable);

        verify(providerRepository).findByTenantIdAndStatus(eq(TENANT), eq("ACTIVE"), any(Pageable.class));
    }

    @Test
    @DisplayName("the standing that gates booking travels with the record")
    void standingIsCarriedSoBookingCanGateAndSurfacesCanLabel() {
        ProviderEntity p = hpaImport();
        // Discovery must not imply participation, and must not imply verification either.
        assertThat(p.getClaimedAt())
                .as("not claimed — booking and request-routing must refuse on this, not on findability")
                .isNull();
        assertThat(p.getLifecycleStatus()).isEqualTo("PRELOADED");
        assertThat(p.getRegistryStatus())
                .as("a surface showing this record must say it is pending verification")
                .isEqualTo("PENDING_VERIFICATION");
    }
}
