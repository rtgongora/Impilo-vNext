package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.core.MyRegulatoryService.MyRegulatorySummary;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderGoodStandingEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderGoodStandingRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.RegisterEntryRestrictionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyRegulatoryServiceTest {

    @Mock ProviderRepository providerRepository;
    @Mock ProviderCouncilRegistrationRecordRepository registrationRepository;
    @Mock ProviderGoodStandingRepository goodStandingRepository;
    @Mock RegisterEntryRestrictionRepository restrictionRepository;

    MyRegulatoryService service;
    UUID tenant;
    UUID healthId;

    @BeforeEach
    void setUp() {
        service = new MyRegulatoryService(providerRepository, registrationRepository,
                goodStandingRepository, restrictionRepository);
        tenant = UUID.randomUUID();
        healthId = UUID.randomUUID();
        lenient().when(restrictionRepository.findByTenantIdAndRegistrationRecordIdInAndStatus(
                eq(tenant), anyList(), eq("ACTIVE"))).thenReturn(List.of());
    }

    @Test
    void unlinkedWhenNoProviderForPerson() {
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenant, healthId)).thenReturn(Optional.empty());
        MyRegulatorySummary summary = service.summaryForPerson(tenant, healthId.toString());
        assertThat(summary.linked()).isFalse();
        assertThat(summary.councils()).isEmpty();
    }

    @Test
    void unlinkedWhenActorIsNotAUuid() {
        MyRegulatorySummary summary = service.summaryForPerson(tenant, "not-a-uuid");
        assertThat(summary.linked()).isFalse();
    }

    @Test
    void assemblesOwnRegistrationsWithGoodStanding() {
        ProviderEntity provider = new ProviderEntity();
        provider.setId(77L);
        provider.setProviderPublicId("PROV000000000000000000000A");
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenant, healthId))
                .thenReturn(Optional.of(provider));

        CouncilEntity ncz = new CouncilEntity();
        ncz.setId(3L);
        ncz.setCouncilCode("NCZ");
        ncz.setName("Nurses Council of Zimbabwe");

        ProviderCouncilRegistrationRecordEntity rec = new ProviderCouncilRegistrationRecordEntity();
        rec.setCouncil(ncz);
        rec.setRegistrationNumber("GN12345");
        rec.setStatus("REGISTERED");
        // id is generated; leave null — the service tolerates null ids for the restriction join.
        when(registrationRepository.findByTenantIdAndProvider_Id(tenant, 77L)).thenReturn(List.of(rec));

        ProviderGoodStandingEntity gs = new ProviderGoodStandingEntity();
        gs.setProviderId(77L);
        gs.setCouncilId(3L);
        gs.setStanding("GOOD");
        when(goodStandingRepository.findByTenantIdAndProviderId(tenant, 77L)).thenReturn(List.of(gs));

        MyRegulatorySummary summary = service.summaryForPerson(tenant, healthId.toString());
        assertThat(summary.linked()).isTrue();
        assertThat(summary.providerPublicId()).isEqualTo("PROV000000000000000000000A");
        assertThat(summary.councils()).hasSize(1);
        assertThat(summary.councils().get(0).councilCode()).isEqualTo("NCZ");
        assertThat(summary.councils().get(0).standing()).isEqualTo("GOOD");
    }
}
