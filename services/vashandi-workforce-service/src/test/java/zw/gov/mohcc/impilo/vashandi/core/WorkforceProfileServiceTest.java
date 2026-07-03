package zw.gov.mohcc.impilo.vashandi.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.integration.FundoIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.IntegrationCheckResult;
import zw.gov.mohcc.impilo.vashandi.integration.VarapiIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.WorkforceGovernanceIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkforceProfileServiceTest {

    @Mock
    WorkforceProfileRepository profileRepository;
    @Mock
    VarapiIntegrationClient varapiClient;
    @Mock
    WorkforceGovernanceIntegrationClient governanceClient;
    @Mock
    FundoIntegrationClient fundoClient;
    @Mock
    VashandiOutboxWriter outboxWriter;

    WorkforceProfileService service;

    @BeforeEach
    void setUp() {
        service = new WorkforceProfileService(
                profileRepository, varapiClient, governanceClient, fundoClient, outboxWriter, new ObjectMapper());
    }

    @Test
    void reconcile_marksPendingBackendWhenDependencyDegraded() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        WorkforceProfileEntity profile = new WorkforceProfileEntity();
        profile.setId(profileId);
        profile.setTenantId(tenant);
        profile.setHealthId("HID-1");
        profile.setProviderWorkerId("PWR-1");

        when(profileRepository.findByTenantIdAndId(tenant, profileId)).thenReturn(Optional.of(profile));
        when(varapiClient.fetchProfessionalStatus("PWR-1"))
                .thenReturn(IntegrationCheckResult.live("varapi", Map.of("status", "active")));
        when(governanceClient.fetchHscEmploymentSummary("HID-1"))
                .thenReturn(IntegrationCheckResult.degraded("workforce-governance", "connection refused"));
        when(fundoClient.fetchTrainingEvidence("PWR-1"))
                .thenReturn(IntegrationCheckResult.live("fundo", Map.of("cpd", "current")));

        VashandiDtos.ReconcileProfileResponse response = service.reconcile(
                tenant, new VashandiDtos.ReconcileProfileRequest(profileId, null, null, null));

        assertThat(response.status()).isEqualTo("pending_backend");
        verify(profileRepository).save(any(WorkforceProfileEntity.class));
        verify(outboxWriter).publish(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void reconcile_createsProfileForFirstTimeProviderWorker() throws Exception {
        UUID tenant = UUID.randomUUID();

        when(profileRepository.findByTenantIdAndProviderWorkerId(tenant, "PROV-ZW-00009"))
                .thenReturn(Optional.empty());
        when(varapiClient.findProvider("PROV-ZW-00009"))
                .thenReturn(Optional.of(Map.of("impiloHealthId", "c0000000-0000-4000-8000-000000000009")));
        when(profileRepository.save(any(WorkforceProfileEntity.class))).thenAnswer(inv -> {
            WorkforceProfileEntity p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
        when(varapiClient.fetchProfessionalStatus("PROV-ZW-00009"))
                .thenReturn(IntegrationCheckResult.live("varapi", Map.of("status", "active")));
        when(governanceClient.fetchHscEmploymentSummary("c0000000-0000-4000-8000-000000000009"))
                .thenReturn(IntegrationCheckResult.live("workforce-governance", Map.of()));
        when(fundoClient.fetchTrainingEvidence("PROV-ZW-00009"))
                .thenReturn(IntegrationCheckResult.live("fundo", Map.of()));

        VashandiDtos.ReconcileProfileResponse response = service.reconcile(
                tenant, new VashandiDtos.ReconcileProfileRequest(
                        null, "PROV-ZW-00009", "PROV-ZW-00009", null));

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.profile().getProviderWorkerId()).isEqualTo("PROV-ZW-00009");
        assertThat(response.profile().getHealthId()).isEqualTo("c0000000-0000-4000-8000-000000000009");
    }
}
