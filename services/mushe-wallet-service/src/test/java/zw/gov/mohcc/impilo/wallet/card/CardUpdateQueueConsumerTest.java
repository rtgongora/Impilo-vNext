package zw.gov.mohcc.impilo.wallet.card;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardUpdateQueueEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardUpdateQueueRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The queue drainer writes each PENDING update through the NFC-writer seam and
 * marks it synced; a writer failure retries and eventually FAILs (no infinite spin).
 */
@ExtendWith(MockitoExtension.class)
class CardUpdateQueueConsumerTest {

    @Mock CardUpdateQueueRepository queueRepository;
    @Mock CardHealthDataService healthDataService;
    @Mock NfcCardWriter nfcCardWriter;

    CardUpdateQueueConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CardUpdateQueueConsumer(queueRepository, healthDataService, nfcCardWriter, 50, 3);
    }

    private CardUpdateQueueEntity pending(long id) {
        CardUpdateQueueEntity e = new CardUpdateQueueEntity();
        e.setId(id);
        e.setQueueId(UUID.randomUUID());
        e.setCardId(UUID.randomUUID());
        e.setTenantId(UUID.randomUUID());
        e.setUpdateType("HEALTH_SUMMARY");
        e.setPayloadEncrypted(new byte[16]);
        e.setStatus("PENDING");
        e.setAttempts(0);
        return e;
    }

    @Test
    @DisplayName("successful write → markSyncedToCard, counted as synced")
    void successfulWriteSyncs() {
        CardUpdateQueueEntity e = pending(1L);
        when(queueRepository.findByStatusOrderByPriorityAscCreatedAtAsc(eq("PENDING"), any(Pageable.class)))
                .thenReturn(List.of(e));
        when(queueRepository.findById(1L)).thenReturn(Optional.of(e));
        when(nfcCardWriter.write(any(), eq("HEALTH_SUMMARY"), any()))
                .thenReturn(new NfcCardWriter.WriteReceipt("SIMULATED_OK", "aid", 2, 16, List.of()));

        var result = consumer.drainPending();

        assertEquals(1, result.processed());
        assertEquals(1, result.synced());
        verify(healthDataService).markSyncedToCard(e.getQueueId());
    }

    @Test
    @DisplayName("write failure below max-attempts → stays PENDING (retry)")
    void writeFailureRetries() {
        CardUpdateQueueEntity e = pending(2L);
        when(queueRepository.findByStatusOrderByPriorityAscCreatedAtAsc(eq("PENDING"), any(Pageable.class)))
                .thenReturn(List.of(e));
        when(queueRepository.findById(2L)).thenReturn(Optional.of(e));
        when(nfcCardWriter.write(any(), any(), any()))
                .thenReturn(NfcCardWriter.WriteReceipt.failed("reader offline"));

        var result = consumer.drainPending();

        assertEquals(1, result.retried());
        assertEquals(0, result.failed());
        assertEquals("PENDING", e.getStatus());
        assertEquals(1, e.getAttempts());
        verify(healthDataService, never()).markSyncedToCard(any());
    }

    @Test
    @DisplayName("write failure at max-attempts → FAILED (no infinite spin)")
    void writeFailureAtMaxFails() {
        CardUpdateQueueEntity e = pending(3L);
        e.setAttempts(2); // next attempt = 3 = maxAttempts
        when(queueRepository.findByStatusOrderByPriorityAscCreatedAtAsc(eq("PENDING"), any(Pageable.class)))
                .thenReturn(List.of(e));
        when(queueRepository.findById(3L)).thenReturn(Optional.of(e));
        when(nfcCardWriter.write(any(), any(), any()))
                .thenThrow(new RuntimeException("hardware fault"));

        var result = consumer.drainPending();

        assertEquals(1, result.failed());
        assertEquals("FAILED", e.getStatus());
    }

    @Test
    @DisplayName("empty queue → no work, no writer calls")
    void emptyQueue() {
        when(queueRepository.findByStatusOrderByPriorityAscCreatedAtAsc(eq("PENDING"), any(Pageable.class)))
                .thenReturn(List.of());

        var result = consumer.drainPending();

        assertEquals(0, result.processed());
        verifyNoInteractions(nfcCardWriter);
    }
}
