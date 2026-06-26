package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.core.CouncilRegistrationResolverService.CouncilProviderRef;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouncilRegistrationResolverServiceTest {

    @Mock
    ProviderCouncilRegistrationRecordRepository registrationRepo;

    CouncilRegistrationResolverService service;

    @BeforeEach
    void setUp() {
        service = new CouncilRegistrationResolverService(registrationRepo);
    }

    private ProviderCouncilRegistrationRecordEntity record(UUID healthId, String publicId,
                                                           String councilCode, String number) {
        ProviderEntity provider = new ProviderEntity();
        provider.setImpiloHealthId(healthId);
        provider.setProviderPublicId(publicId);
        CouncilEntity council = new CouncilEntity();
        council.setCouncilCode(councilCode);
        ProviderCouncilRegistrationRecordEntity rec = new ProviderCouncilRegistrationRecordEntity();
        rec.setProvider(provider);
        rec.setCouncil(council);
        rec.setRegistrationNumber(number);
        rec.setStatus("REGISTERED");
        return rec;
    }

    @Test
    void resolvesByCouncilCodeAndNumber_neverAuthenticates() {
        UUID tenant = UUID.randomUUID();
        UUID healthId = UUID.randomUUID();
        when(registrationRepo
                .findFirstByTenantIdAndCouncil_CouncilCodeIgnoreCaseAndRegistrationNumberIgnoreCase(
                        tenant, "MDPCZ", "EC-12345"))
                .thenReturn(Optional.of(record(healthId, "PUB-1", "MDPCZ", "EC-12345")));

        Optional<CouncilProviderRef> ref = service.resolve(tenant, "MDPCZ", "EC-12345");

        assertThat(ref).isPresent();
        assertThat(ref.get().impiloHealthId()).isEqualTo(healthId.toString());
        assertThat(ref.get().providerPublicId()).isEqualTo("PUB-1");
        assertThat(ref.get().councilCode()).isEqualTo("MDPCZ");
        assertThat(ref.get().canAuthenticate()).isFalse();
    }

    @Test
    void resolvesByNumberOnly_whenNoCouncilCode() {
        UUID tenant = UUID.randomUUID();
        UUID healthId = UUID.randomUUID();
        when(registrationRepo.findFirstByTenantIdAndRegistrationNumberIgnoreCase(tenant, "EC-9"))
                .thenReturn(Optional.of(record(healthId, "PUB-2", "NMCZ", "EC-9")));

        Optional<CouncilProviderRef> ref = service.resolve(tenant, null, "EC-9");

        assertThat(ref).isPresent();
        assertThat(ref.get().canAuthenticate()).isFalse();
    }

    @Test
    void missReturnsEmpty() {
        UUID tenant = UUID.randomUUID();
        when(registrationRepo.findFirstByTenantIdAndRegistrationNumberIgnoreCase(tenant, "NOPE"))
                .thenReturn(Optional.empty());
        assertThat(service.resolve(tenant, null, "NOPE")).isEmpty();
    }

    @Test
    void blankNumberReturnsEmpty() {
        assertThat(service.resolve(UUID.randomUUID(), "MDPCZ", "  ")).isEmpty();
        assertThat(service.resolve(UUID.randomUUID(), null, null)).isEmpty();
    }
}
