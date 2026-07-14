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
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecognitionResponse;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Recognition rules for provider-as-service-seeker advantages:
 * only canonical-lifecycle-active providers are recognised, and every
 * negative outcome (unknown / malformed / suspended) yields the IDENTICAL
 * generic body — the EligibilityController enumeration-resistance doctrine.
 */
@ExtendWith(MockitoExtension.class)
class ProviderRecognitionServiceTest {

    @Mock private ProviderRepository providerRepository;

    private ProviderRecognitionService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID healthId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProviderRecognitionService(providerRepository);
        TrustContextHolder.set(new TrustContext(
                tenantId, "experience-bff", "SYSTEM", "TREATMENT", "device",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private ProviderEntity provider(String lifecycleStatus) {
        ProviderEntity p = new ProviderEntity();
        p.setId(7L);
        p.setTenantId(tenantId);
        p.setImpiloHealthId(healthId);
        p.setProfession("Medical Doctor");
        p.setCadre("GENERAL_PRACTITIONER");
        p.setLifecycleStatus(lifecycleStatus);
        p.deriveStatusProjections();
        return p;
    }

    @Test
    void licencedActiveProviderIsRecognisedWithDetail() {
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, healthId))
                .thenReturn(Optional.of(provider("LICENCED_ACTIVE")));

        ProviderRecognitionResponse r = service.recognitionByHealthId(healthId.toString());

        assertThat(r.recognised()).isTrue();
        assertThat(r.licenceStatus()).isEqualTo("ACTIVE");
        assertThat(r.profession()).isEqualTo("Medical Doctor");
        assertThat(r.cadre()).isEqualTo("GENERAL_PRACTITIONER");
    }

    @Test
    void registeredProviderIsRecognised() {
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, healthId))
                .thenReturn(Optional.of(provider("REGISTERED")));

        assertThat(service.recognitionByHealthId(healthId.toString()).recognised()).isTrue();
    }

    @Test
    void suspendedLicenceIsNotRecognised_andBodyIsGeneric() {
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, healthId))
                .thenReturn(Optional.of(provider("SUSPENDED")));

        ProviderRecognitionResponse suspended = service.recognitionByHealthId(healthId.toString());

        assertThat(suspended.recognised()).isFalse();
        // Enumeration resistance: NO leaked detail for a known-but-suspended provider.
        assertThat(suspended).isEqualTo(ProviderRecognitionResponse.notRecognised());
    }

    @Test
    void lapsedLicenceIsNotRecognised() {
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, healthId))
                .thenReturn(Optional.of(provider("LAPSED")));

        assertThat(service.recognitionByHealthId(healthId.toString()))
                .isEqualTo(ProviderRecognitionResponse.notRecognised());
    }

    @Test
    void unknownHealthIdYieldsIdenticalGenericBody() {
        UUID unknown = UUID.randomUUID();
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, unknown))
                .thenReturn(Optional.empty());
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, healthId))
                .thenReturn(Optional.of(provider("SUSPENDED")));

        ProviderRecognitionResponse forUnknown = service.recognitionByHealthId(unknown.toString());
        ProviderRecognitionResponse forSuspended = service.recognitionByHealthId(healthId.toString());

        // The two negatives are byte-for-byte the same shape and content.
        assertThat(forUnknown).isEqualTo(forSuspended);
        assertThat(forUnknown).isEqualTo(ProviderRecognitionResponse.notRecognised());
    }

    @Test
    void malformedHealthIdYieldsGenericBodyWithoutError() {
        ProviderRecognitionResponse r = service.recognitionByHealthId("not-a-uuid");
        assertThat(r).isEqualTo(ProviderRecognitionResponse.notRecognised());
    }
}
