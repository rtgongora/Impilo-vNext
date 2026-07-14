package zw.gov.mohcc.impilo.costa.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.config.CostaProperties;
import zw.gov.mohcc.impilo.costa.domain.entity.EncounterEntity;
import zw.gov.mohcc.impilo.costa.integration.ProviderRecognitionClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Encounter enrichment guard rails: config-gated (disabled by default),
 * never overwrites, FAIL-OPEN when recognition sources are down, and tenant
 * class policy is enforced.
 */
@ExtendWith(MockitoExtension.class)
class HealthWorkerExemptionEnrichmentTest {

    @Mock private ProviderRecognitionClient recognitionClient;

    private CostaProperties properties;
    private HealthWorkerExemptionEnrichment enrichment;
    private EncounterEntity encounter;
    private final UUID tenantId = UUID.randomUUID();
    private final String healthId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        properties = new CostaProperties();
        properties.getRecognition().setEnabled(true);
        enrichment = new HealthWorkerExemptionEnrichment(recognitionClient, properties);
        encounter = new EncounterEntity();
        encounter.setEncounterId("ENC-1");
        encounter.setTenantId(tenantId);
        encounter.setPatientCpid(healthId);
    }

    @Test
    void recognisedHealthWorkerGetsTagged() {
        when(recognitionClient.resolve(tenantId, healthId))
                .thenReturn(new ProviderRecognitionClient.Recognition(true, "LICENSED_PROVIDER"));

        enrichment.enrich(encounter);

        assertThat(encounter.getExemptionCategory()).isEqualTo("HEALTH_WORKER");
    }

    @Test
    void disabledConfigMeansNoLookupAndNoTag() {
        properties.getRecognition().setEnabled(false);

        enrichment.enrich(encounter);

        assertThat(encounter.getExemptionCategory()).isNull();
        verify(recognitionClient, never()).resolve(any(), any());
    }

    @Test
    void existingExemptionCategoryIsNeverOverwritten() {
        encounter.setExemptionCategory("INDIGENT");
        lenient().when(recognitionClient.resolve(tenantId, healthId))
                .thenReturn(new ProviderRecognitionClient.Recognition(true, "LICENSED_PROVIDER"));

        enrichment.enrich(encounter);

        assertThat(encounter.getExemptionCategory()).isEqualTo("INDIGENT");
        verify(recognitionClient, never()).resolve(any(), any());
    }

    @Test
    void notRecognisedLeavesEncounterUntouched() {
        when(recognitionClient.resolve(tenantId, healthId))
                .thenReturn(ProviderRecognitionClient.Recognition.notRecognised());

        enrichment.enrich(encounter);

        assertThat(encounter.getExemptionCategory()).isNull();
    }

    @Test
    void recognitionSourceFailureFailsOpenToNoDiscount() {
        when(recognitionClient.resolve(tenantId, healthId))
                .thenThrow(new RuntimeException("registries down"));

        enrichment.enrich(encounter); // must not throw — billing never blocks

        assertThat(encounter.getExemptionCategory()).isNull();
    }

    @Test
    void tenantClassPolicyIsEnforced() {
        properties.getRecognition().setAllowedClasses(List.of("LICENSED_PROVIDER"));
        when(recognitionClient.resolve(tenantId, healthId))
                .thenReturn(new ProviderRecognitionClient.Recognition(true, "HEALTH_WORKFORCE"));

        enrichment.enrich(encounter);

        assertThat(encounter.getExemptionCategory()).isNull();
    }
}
