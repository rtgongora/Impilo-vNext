package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.varapi.enums.ProviderRegistryStatus;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProviderClaimAdjudicationServiceTest {

    @Mock ProviderRepository providerRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock ProviderWorkflowServiceClient workflowClient;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CLAIMANT = UUID.randomUUID();

    private ProviderClaimAdjudicationService service() {
        return new ProviderClaimAdjudicationService(providerRepository, outboxRepository, workflowClient);
    }

    @Test
    void escalateIsNoOpWhenAdjudicationDisabled() {
        when(workflowClient.isEnabled()).thenReturn(false);

        service().escalate(TENANT, 42L, CLAIMANT, "actor-1", null);

        verify(providerRepository, never()).findByIdAndTenantId(any(), any());
        verify(providerRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void escalateMarksConflictStartsWorkflowAndEmitsWhenEnabled() {
        when(workflowClient.isEnabled()).thenReturn(true);
        ProviderEntity existing = new ProviderEntity();
        existing.setProviderPublicId("PUB1234567890");
        when(providerRepository.findByIdAndTenantId(42L, TENANT)).thenReturn(Optional.of(existing));
        when(providerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().escalate(TENANT, 42L, CLAIMANT, "actor-1", "corr-1");

        assertEquals(ProviderRegistryStatus.CONFLICT.name(), existing.getRegistryStatus());
        verify(workflowClient).startAdjudication(eq(TENANT), eq("PUB1234567890"), any(), anyString(), any());
        verify(outboxRepository).save(any());
    }

    @Test
    void escalateIsSafeWhenProviderMissing() {
        when(workflowClient.isEnabled()).thenReturn(true);
        when(providerRepository.findByIdAndTenantId(42L, TENANT)).thenReturn(Optional.empty());

        service().escalate(TENANT, 42L, CLAIMANT, "actor-1", null);

        verify(providerRepository, never()).save(any());
        verify(workflowClient, never()).startAdjudication(any(), anyString(), any(), anyString(), any());
        verify(outboxRepository, never()).save(any());
    }
}
