package zw.gov.mohcc.impilo.costa.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.support.Acknowledgment;
import zw.gov.mohcc.impilo.costa.domain.entity.InsurancePlanEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.InsurancePlanRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Coverage-plan sync contract: a plan created at the coverage SoR lands in
 * COSTA as a PENDING_TERMS placeholder with ZERO terms (money splits are
 * governed COSTA config, never inferred from an event), known plans are not
 * duplicated, and processing failures are never ack-dropped.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoveragePlanConsumerTest {

    @Mock InsurancePlanRepository repo;
    @Mock Acknowledgment ack;

    private final UUID tenantId = UUID.randomUUID();

    private CoveragePlanConsumer consumer() {
        return new CoveragePlanConsumer(repo, new ObjectMapper());
    }

    private String planEvent(String code) {
        return "{\"plan_code\":\"" + code + "\",\"plan_name\":\"Test Plan\","
                + "\"payer_id\":\"PAYER-1\",\"status\":\"ACTIVE\",\"tenantId\":\"" + tenantId + "\"}";
    }

    @Test
    void unknownPlan_savedAsPendingTermsPlaceholder_withZeroTerms() {
        when(repo.findByTenantIdAndPlanCodeAndStatus(eq(tenantId), eq("COV-NEW"), any()))
                .thenReturn(Optional.empty());

        consumer().onCoveragePlanEvent(planEvent("COV-NEW"), ack);

        ArgumentCaptor<InsurancePlanEntity> captor = ArgumentCaptor.forClass(InsurancePlanEntity.class);
        verify(repo).save(captor.capture());
        InsurancePlanEntity saved = captor.getValue();
        assertEquals("COV-NEW", saved.getPlanCode());
        assertEquals(CoveragePlanConsumer.STATUS_PENDING_TERMS, saved.getStatus());
        assertEquals(BigDecimal.ZERO, saved.getCoveragePct());
        assertEquals(BigDecimal.ZERO, saved.getCopayPct());
        verify(ack).acknowledge();
    }

    @Test
    void knownActivePlan_notDuplicated() {
        when(repo.findByTenantIdAndPlanCodeAndStatus(tenantId, "COV-MOHCC-CORE", "ACTIVE"))
                .thenReturn(Optional.of(new InsurancePlanEntity()));

        consumer().onCoveragePlanEvent(planEvent("COV-MOHCC-CORE"), ack);

        verify(repo, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void processingFailure_rethrows_neverAckDrops() {
        when(repo.findByTenantIdAndPlanCodeAndStatus(any(), any(), any()))
                .thenThrow(new RuntimeException("db down"));

        assertThrows(MoneyEventProcessingException.class,
                () -> consumer().onCoveragePlanEvent(planEvent("COV-X"), ack));
        verify(ack, never()).acknowledge();
    }

    @Test
    void malformedEvent_missingPlanCode_ackedAndSkipped() {
        consumer().onCoveragePlanEvent("{\"tenantId\":\"" + tenantId + "\"}", ack);
        verify(repo, never()).save(any());
        verify(ack).acknowledge();
    }
}
