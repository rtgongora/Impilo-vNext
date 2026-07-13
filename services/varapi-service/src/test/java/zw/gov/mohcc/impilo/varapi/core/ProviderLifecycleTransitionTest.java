package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderContactRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilAffiliationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderIdentifierRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderSpecialtyRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProviderLifecycleTransitionTest {

    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderIdentifierRepository identifierRepository;
    @Mock private ProviderSpecialtyRepository specialtyRepository;
    @Mock private ProviderContactRepository contactRepository;
    @Mock private ProviderCouncilAffiliationRepository affiliationRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private VarapiProperties varapiProperties;

    private ProviderService service;

    @BeforeEach
    void setUp() {
        service = new ProviderService(providerRepository, identifierRepository, specialtyRepository,
                contactRepository, affiliationRepository, outboxRepository, varapiProperties);
        TrustContextHolder.set(new TrustContext(UUID.randomUUID(), "registrar-1", "PROVIDER", "REGISTRY",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clear() {
        TrustContextHolder.clear();
    }

    private ProviderEntity provider(String lifecycle) {
        ProviderEntity p = new ProviderEntity();
        p.setProviderPublicId("PRV-1");
        p.setLifecycleStatus(lifecycle);
        p.setVersion(1);
        return p;
    }

    @Test
    void suspend_fromLicencedActive_succeedsAndProjects() {
        when(providerRepository.findByProviderPublicId("PRV-1")).thenReturn(Optional.of(provider("LICENCED_ACTIVE")));
        when(providerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.transitionLifecycle("PRV-1", "SUSPENDED", "Interim order", null, null);

        assertEquals("SUSPENDED", result.provider().getLifecycleStatus());
        assertEquals("SUSPENDED", result.provider().getStatus());
        verify(outboxRepository).save(any());
    }

    @Test
    void invalidTransition_isRejected() {
        when(providerRepository.findByProviderPublicId("PRV-1")).thenReturn(Optional.of(provider("LICENCED_ACTIVE")));
        assertThrows(IllegalStateException.class,
                () -> service.transitionLifecycle("PRV-1", "REGISTERED", null, null, null));
    }

    @Test
    void terminalTransition_requiresReason() {
        when(providerRepository.findByProviderPublicId("PRV-1")).thenReturn(Optional.of(provider("LICENCED_ACTIVE")));
        assertThrows(IllegalArgumentException.class,
                () -> service.transitionLifecycle("PRV-1", "RETIRED", null, null, null));
    }

    @Test
    void deceased_requiresSourceRef() {
        when(providerRepository.findByProviderPublicId("PRV-1")).thenReturn(Optional.of(provider("LICENCED_ACTIVE")));
        assertThrows(IllegalArgumentException.class,
                () -> service.transitionLifecycle("PRV-1", "DECEASED", "Confirmed", null, null));
    }

    @Test
    void allowedTransitions_forLicencedActive() {
        when(providerRepository.findByProviderPublicId("PRV-1")).thenReturn(Optional.of(provider("LICENCED_ACTIVE")));
        var allowed = service.allowedLifecycleTransitions("PRV-1");
        assertEquals("LICENCED_ACTIVE", allowed.current());
        assertTrue(allowed.allowed().contains("SUSPENDED"));
        assertTrue(allowed.allowed().contains("RETIRED"));
    }
}
