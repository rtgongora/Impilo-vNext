package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * OF-B12 — the §13.3.1 cross-matching sweep: UNBOUND_DISPENSE (completed
 * medication-profile dispense without a claim) and CLAIM_EPISODE_TTL_EXPIRED
 * (claim-carrying episode that never dispensed within TTL), each emitted
 * exactly once via the flag column.
 */
@ExtendWith(MockitoExtension.class)
class DispenseClaimAnomalyJobTest {

    @Mock DispenseOrderRepository orderRepository;
    @Mock EventOutboxRepository outboxRepository;

    private DispenseClaimAnomalyJob job;

    @BeforeEach
    void setUp() {
        job = new DispenseClaimAnomalyJob(orderRepository, outboxRepository,
                new ObjectMapper(), 72, 30);
    }

    private DispenseOrderEntity order(DispenseStatus status, UUID claimId) {
        DispenseOrderEntity order = new DispenseOrderEntity();
        order.setOrderId(UUID.randomUUID());
        order.setTenantId(UUID.randomUUID());
        order.setFacilityId(UUID.randomUUID());
        order.setPatientCpid("CPID-1");
        order.setOrosOrderId("01OROSORDERCCCCCCCCCCCCCCC");
        order.setStatus(status);
        order.setPrescriptionClaimId(claimId);
        order.setCompletedAt(OffsetDateTime.now().minusHours(2));
        return order;
    }

    @Test
    @DisplayName("completed dispense without a claim → UNBOUND_DISPENSE emitted + row flagged")
    void unboundDispenseEmitsAnomaly() {
        DispenseOrderEntity unbound = order(DispenseStatus.DISPENSED, null);
        when(orderRepository.findUnboundDispenseAnomalies(any())).thenReturn(List.of(unbound));
        when(orderRepository.findStalledClaimEpisodes(any())).thenReturn(List.of());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.sweep();

        verify(outboxRepository).save(argThat(e ->
                "DISPENSE_CLAIM_ANOMALY".equals(e.getEventType())
                        && e.getPayload().contains("UNBOUND_DISPENSE")));
        assertThat(unbound.getClaimAnomalyFlaggedAt()).as("emit-once flag").isNotNull();
    }

    @Test
    @DisplayName("claim-carrying episode stalled past TTL → CLAIM_EPISODE_TTL_EXPIRED emitted")
    void stalledClaimEmitsAnomaly() {
        DispenseOrderEntity stalled = order(DispenseStatus.PENDING, UUID.randomUUID());
        when(orderRepository.findUnboundDispenseAnomalies(any())).thenReturn(List.of());
        when(orderRepository.findStalledClaimEpisodes(any())).thenReturn(List.of(stalled));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.sweep();

        verify(outboxRepository).save(argThat(e ->
                "DISPENSE_CLAIM_ANOMALY".equals(e.getEventType())
                        && e.getPayload().contains("CLAIM_EPISODE_TTL_EXPIRED")
                        && e.getPayload().contains(stalled.getPrescriptionClaimId().toString())));
        assertThat(stalled.getClaimAnomalyFlaggedAt()).isNotNull();
    }

    @Test
    @DisplayName("clean estate → no anomaly events")
    void cleanEstateEmitsNothing() {
        when(orderRepository.findUnboundDispenseAnomalies(any())).thenReturn(List.of());
        when(orderRepository.findStalledClaimEpisodes(any())).thenReturn(List.of());

        job.sweep();

        verifyNoInteractions(outboxRepository);
    }
}
