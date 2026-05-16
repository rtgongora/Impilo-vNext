package zw.gov.mohcc.impilo.mushex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.kafka.OutboxPublisher;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OutboxPublisher}.
 *
 * Validates topic routing for each event type, batch publishing behavior,
 * marking events as published, and graceful Kafka error handling.
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock private EventOutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxRepository, kafkaTemplate, true, "core.transaction.events");
    }

    // ---------------------------------------------------------------
    // routeTopic
    // ---------------------------------------------------------------

    @Test
    void routeTopic_intentCreated_returnsCorrectTopic() {
        assertEquals("mushex.payment.intent.created",
            OutboxPublisher.routeTopic("INTENT_CREATED"));
    }

    @Test
    void routeTopic_statusChanged_returnsCorrectTopic() {
        assertEquals("mushex.payment.status.changed",
            OutboxPublisher.routeTopic("STATUS_CHANGED"));
    }

    @Test
    void routeTopic_refundRequested_returnsRefundTopic() {
        assertEquals("mushex.refund.status.changed",
            OutboxPublisher.routeTopic("REFUND_REQUESTED"));
    }

    @Test
    void routeTopic_refundCompleted_returnsRefundTopic() {
        assertEquals("mushex.refund.status.changed",
            OutboxPublisher.routeTopic("REFUND_COMPLETED"));
    }

    @Test
    void routeTopic_refundFailed_returnsRefundTopic() {
        assertEquals("mushex.refund.status.changed",
            OutboxPublisher.routeTopic("REFUND_FAILED"));
    }

    @Test
    void routeTopic_claimSubmitted_returnsCorrectTopic() {
        assertEquals("mushex.claim.submitted",
            OutboxPublisher.routeTopic("CLAIM_SUBMITTED"));
    }

    @Test
    void routeTopic_claimAdjudicated_returnsCorrectTopic() {
        assertEquals("mushex.claim.adjudicated",
            OutboxPublisher.routeTopic("CLAIM_ADJUDICATED"));
    }

    @Test
    void routeTopic_claimPaid_routesToClaimAdjudicatedTopic() {
        assertEquals("mushex.claim.adjudicated",
            OutboxPublisher.routeTopic("CLAIM_PAID"));
    }

    @Test
    void routeTopic_settlementBatchReleased_returnsCorrectTopic() {
        assertEquals("mushex.settlement.batch.released",
            OutboxPublisher.routeTopic("SETTLEMENT_BATCH_RELEASED"));
    }

    @Test
    void routeTopic_fraudFlagged_returnsCorrectTopic() {
        assertEquals("mushex.fraud.flagged",
            OutboxPublisher.routeTopic("FRAUD_FLAGGED"));
    }

    @Test
    void routeTopic_remittanceIssued_returnsCorrectTopic() {
        assertEquals("mushex.remittance.issued",
            OutboxPublisher.routeTopic("REMITTANCE_ISSUED"));
    }

    @Test
    void routeTopic_remittanceClaimed_returnsCorrectTopic() {
        assertEquals("mushex.remittance.claimed",
            OutboxPublisher.routeTopic("REMITTANCE_CLAIMED"));
    }

    @Test
    void routeTopic_unknownType_returnsDefaultTopic() {
        assertEquals("mushex.events",
            OutboxPublisher.routeTopic("SOME_UNKNOWN_EVENT"));
    }

    @Test
    void resolveCoreTransactionTopic_statusChanged_routesToCoreTransactionEvents() {
        assertEquals("core.transaction.events",
            OutboxPublisher.resolveCoreTransactionTopic("STATUS_CHANGED"));
    }

    @Test
    void resolveCoreTransactionTopic_nonCanonical_returnsNull() {
        assertNull(OutboxPublisher.resolveCoreTransactionTopic("FRAUD_FLAGGED"));
    }

    @Test
    void routingConvention_producedTypesAreExplicitOrIntentionalDefaults() {
        Set<String> producedEventTypes = Set.of(
                "INTENT_CREATED",
                "STATUS_CHANGED",
                "REFUND_REQUESTED",
                "REFUND_COMPLETED",
                "REFUND_FAILED",
                "CLAIM_SUBMITTED",
                "CLAIM_ADJUDICATED",
                "CLAIM_PAID",
                "CLAIM_DISPUTED",
                "SETTLEMENT_COMPUTED",
                "SETTLEMENT_BATCH_RELEASED",
                "FRAUD_FLAGGED",
                "REMITTANCE_ISSUED",
                "REMITTANCE_CLAIMED",
                "REMITTANCE_REVOKED",
                "REMITTANCE_REQUESTED",
                "WALLET_CREATED",
                "WALLET_TRANSACTION_RECORDED",
                "LEDGER_ENTRY_POSTED",
                "RECON_MATCHED",
                "ATTEMPT_INITIATED",
                "ATTEMPT_FAILED_PRE_INITIATION",
                "ATTEMPT_RESELECTED"
        );

        Set<String> intentionalDefaults = Set.of(
                "CLAIM_DISPUTED",
                "SETTLEMENT_COMPUTED",
                "REMITTANCE_REVOKED",
                "LEDGER_ENTRY_POSTED",
                "RECON_MATCHED"
        );

        for (String eventType : producedEventTypes) {
            String topic = OutboxPublisher.routeTopic(eventType);
            if (intentionalDefaults.contains(eventType)) {
                assertEquals("mushex.events", topic, "intentional default changed unexpectedly: " + eventType);
            } else {
                assertNotEquals("mushex.events", topic, "event type unexpectedly routed to default topic: " + eventType);
            }
        }
    }

    // ---------------------------------------------------------------
    // publishPendingEvents
    // ---------------------------------------------------------------

    @Test
    void publishPendingEvents_publishesAndMarksAsPublished() {
        EventOutboxEntity event1 = buildOutboxEvent(1L, "INTENT_CREATED", "AGG-001", "{\"data\":1}");
        EventOutboxEntity event2 = buildOutboxEvent(2L, "STATUS_CHANGED", "AGG-002", "{\"data\":2}");

        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
            .thenReturn(List.of(event1, event2));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(outboxRepository.save(any(EventOutboxEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        publisher.publishPendingEvents();

        // Verify Kafka sends for both events
        verify(kafkaTemplate).send("mushex.payment.intent.created", "AGG-001", "{\"data\":1}");
        verify(kafkaTemplate).send("mushex.payment.status.changed", "AGG-002", "{\"data\":2}");

        // Verify both events were saved with publishedAt set
        verify(outboxRepository, times(2)).save(any(EventOutboxEntity.class));
        assertNotNull(event1.getPublishedAt());
        assertNotNull(event2.getPublishedAt());
    }

    @Test
    void publishPendingEvents_emptyQueue_doesNothing() {
        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
            .thenReturn(Collections.emptyList());

        publisher.publishPendingEvents();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(outboxRepository, never()).save(any(EventOutboxEntity.class));
    }

    @Test
    void publishPendingEvents_handlesKafkaErrorGracefully() {
        EventOutboxEntity event1 = buildOutboxEvent(1L, "INTENT_CREATED", "AGG-010", "{\"ok\":true}");
        EventOutboxEntity event2 = buildOutboxEvent(2L, "FRAUD_FLAGGED", "AGG-011", "{\"ok\":true}");

        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
            .thenReturn(List.of(event1, event2));

        // First send throws, second succeeds
        when(kafkaTemplate.send(eq("mushex.payment.intent.created"), anyString(), anyString()))
            .thenThrow(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(eq("mushex.fraud.flagged"), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(outboxRepository.save(any(EventOutboxEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // Should not throw -- errors are handled gracefully per event
        assertDoesNotThrow(() -> publisher.publishPendingEvents());

        // Failed event should NOT have publishedAt set
        assertNull(event1.getPublishedAt());

        // Successful event should have publishedAt set
        assertNotNull(event2.getPublishedAt());

        // Only the successful event should be saved
        verify(outboxRepository, times(1)).save(any(EventOutboxEntity.class));
    }

    @Test
    void publishPendingEvents_usesAggregateIdAsKafkaKey() {
        EventOutboxEntity event = buildOutboxEvent(1L, "CLAIM_SUBMITTED", "CLM-001", "{\"claim\":true}");

        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
            .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(outboxRepository.save(any(EventOutboxEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        publisher.publishPendingEvents();

        // The aggregate ID should be used as the Kafka message key for partitioning
        verify(kafkaTemplate).send("mushex.claim.submitted", "CLM-001", "{\"claim\":true}");
    }

    @Test
    void publishPendingEvents_processesUpTo100Events() {
        EventOutboxEntity event = buildOutboxEvent(1L, "STATUS_CHANGED", "AGG-099", "{}");

        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
            .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(outboxRepository.save(any(EventOutboxEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        publisher.publishPendingEvents();

        // Verify the repository query method was called (findTop100...)
        verify(outboxRepository).findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private EventOutboxEntity buildOutboxEvent(Long id, String eventType,
                                               String aggregateId, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setId(id);
        event.setAggregateType("PAYMENT_INTENT");
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(UUID.randomUUID());
        event.setCreatedAt(OffsetDateTime.now());
        // publishedAt is null (pending)
        return event;
    }
}
