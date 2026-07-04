package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueItemRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link QueueEngine}.
 *
 * <p>Validates queue item creation, token assignment, call-next priority
 * ordering, status transitions, and inter-queue transfers.</p>
 */
@ExtendWith(MockitoExtension.class)
class QueueEngineTest {

    @Mock
    private QueueItemRepository queueItemRepository;

    @Mock
    private JourneyRepository journeyRepository;

    @Mock
    private JourneyStateMachine journeyStateMachine;

    @Mock
    private EventOutboxRepository outboxRepository;

    private QueueEngine queueEngine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID QUEUE_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "actor-123";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        queueEngine = new QueueEngine(
                queueItemRepository, journeyRepository,
                journeyStateMachine, outboxRepository, objectMapper);
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    private JourneyEntity createJourney(String journeyId) {
        JourneyEntity journey = new JourneyEntity();
        journey.setJourneyId(journeyId);
        journey.setTenantId(TENANT_ID);
        journey.setFacilityId(FACILITY_ID);
        journey.setPatientCpid("CPID-001");
        journey.setState(JourneyState.TRIAGED);
        return journey;
    }

    @Nested
    @DisplayName("Enqueue Operations")
    class EnqueueOperations {

        @Test
        @DisplayName("enqueue creates item with auto-assigned token number")
        void enqueueCreatesItemWithToken() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                String journeyId = "J-TEST-001";
                JourneyEntity journey = createJourney(journeyId);

                when(journeyRepository.findByJourneyId(journeyId))
                        .thenReturn(Optional.of(journey));
                when(queueItemRepository.findMaxTokenNumberByQueueId(QUEUE_ID))
                        .thenReturn(5);
                when(queueItemRepository.save(any(QueueItemEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));
                when(journeyStateMachine.transition(any(), any()))
                        .thenReturn(journey);

                QueueItemEntity item = queueEngine.enqueue(QUEUE_ID, journeyId, 3);

                assertThat(item).isNotNull();
                assertThat(item.getQueueId()).isEqualTo(QUEUE_ID);
                assertThat(item.getJourneyId()).isEqualTo(journeyId);
                assertThat(item.getTokenNumber()).isEqualTo(6); // max(5) + 1
                assertThat(item.getPriority()).isEqualTo(3);
                assertThat(item.getStatus()).isEqualTo(QueueItemStatus.WAITING);

                verify(queueItemRepository).save(any(QueueItemEntity.class));
                verify(journeyStateMachine).transition(journey, JourneyState.QUEUED);
            }
        }

        @Test
        @DisplayName("enqueue throws when journey not found")
        void enqueueThrowsWhenJourneyNotFound() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                when(journeyRepository.findByJourneyId("NONEXISTENT"))
                        .thenReturn(Optional.empty());

                assertThatThrownBy(() -> queueEngine.enqueue(QUEUE_ID, "NONEXISTENT", 3))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Journey not found");
            }
        }
    }

    @Nested
    @DisplayName("Call Next Operations")
    class CallNextOperations {

        @Test
        @DisplayName("callNext returns highest priority WAITING item")
        void callNextReturnsHighestPriority() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                QueueItemEntity expectedItem = new QueueItemEntity();
                expectedItem.setId(UUID.randomUUID());
                expectedItem.setQueueId(QUEUE_ID);
                expectedItem.setJourneyId("J-URGENT");
                expectedItem.setPriority(5);
                expectedItem.setTokenNumber(3);
                expectedItem.setStatus(QueueItemStatus.WAITING);
                expectedItem.setCreatedAt(OffsetDateTime.now());

                when(queueItemRepository.findFirstByQueueIdAndStatusOrderByPriorityDescCreatedAtAsc(
                        QUEUE_ID, QueueItemStatus.WAITING))
                        .thenReturn(Optional.of(expectedItem));
                when(queueItemRepository.save(any(QueueItemEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                QueueItemEntity called = queueEngine.callNext(QUEUE_ID);

                assertThat(called).isNotNull();
                assertThat(called.getStatus()).isEqualTo(QueueItemStatus.CALLED);
                assertThat(called.getCalledBy()).isEqualTo(ACTOR_ID);
                assertThat(called.getCalledAt()).isNotNull();
            }
        }

        @Test
        @DisplayName("callNext returns null when queue is empty")
        void callNextReturnsNullWhenEmpty() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                when(queueItemRepository.findFirstByQueueIdAndStatusOrderByPriorityDescCreatedAtAsc(
                        QUEUE_ID, QueueItemStatus.WAITING))
                        .thenReturn(Optional.empty());

                QueueItemEntity called = queueEngine.callNext(QUEUE_ID);

                assertThat(called).isNull();
                verify(queueItemRepository, never()).save(any());
            }
        }
    }

    @Nested
    @DisplayName("Status Transitions")
    class StatusTransitions {

        @Test
        @DisplayName("updateItemStatus to IN_SERVICE transitions journey to IN_SERVICE")
        void statusToInServiceTransitionsJourney() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();
                QueueItemEntity item = new QueueItemEntity();
                item.setId(itemId);
                item.setQueueId(QUEUE_ID);
                item.setJourneyId("J-TEST-001");
                item.setStatus(QueueItemStatus.CALLED);

                JourneyEntity journey = createJourney("J-TEST-001");

                when(queueItemRepository.findById(itemId)).thenReturn(Optional.of(item));
                when(journeyRepository.findByJourneyId("J-TEST-001")).thenReturn(Optional.of(journey));
                when(journeyStateMachine.transition(any(), any())).thenReturn(journey);
                when(queueItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                QueueItemEntity result = queueEngine.updateItemStatus(itemId, QueueItemStatus.IN_SERVICE);

                assertThat(result.getStatus()).isEqualTo(QueueItemStatus.IN_SERVICE);
                assertThat(result.getServiceStartedAt()).isNotNull();
                verify(journeyStateMachine).transition(journey, JourneyState.IN_SERVICE);
            }
        }

        @Test
        @DisplayName("updateItemStatus to IN_TRIAGE records caller and leaves journey untouched (BFF triage regression)")
        void statusToInTriageRecordsCallerWithoutJourneyTransition() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();
                QueueItemEntity item = new QueueItemEntity();
                item.setId(itemId);
                item.setQueueId(QUEUE_ID);
                item.setJourneyId("J-TEST-001");
                item.setStatus(QueueItemStatus.WAITING);

                when(queueItemRepository.findById(itemId)).thenReturn(Optional.of(item));
                when(queueItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                // The BFF triage action sends the literal status string; this is the
                // regression guard for the former 500 (enum had no IN_TRIAGE value).
                QueueItemStatus parsed = QueueItemStatus.valueOf("IN_TRIAGE");
                QueueItemEntity result = queueEngine.updateItemStatus(itemId, parsed);

                assertThat(result.getStatus()).isEqualTo(QueueItemStatus.IN_TRIAGE);
                assertThat(result.getCalledBy()).isEqualTo(ACTOR_ID);
                assertThat(result.getCalledAt()).isNotNull();
                verify(journeyStateMachine, never()).transition(any(), any());
            }
        }

        @Test
        @DisplayName("updateItemStatus to IN_TRIAGE preserves an existing CALLED timestamp")
        void statusToInTriageKeepsExistingCallRecord() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();
                OffsetDateTime originalCallTime = OffsetDateTime.now().minusMinutes(5);
                QueueItemEntity item = new QueueItemEntity();
                item.setId(itemId);
                item.setQueueId(QUEUE_ID);
                item.setJourneyId("J-TEST-001");
                item.setStatus(QueueItemStatus.CALLED);
                item.setCalledBy("earlier-actor");
                item.setCalledAt(originalCallTime);

                when(queueItemRepository.findById(itemId)).thenReturn(Optional.of(item));
                when(queueItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                QueueItemEntity result = queueEngine.updateItemStatus(itemId, QueueItemStatus.IN_TRIAGE);

                assertThat(result.getStatus()).isEqualTo(QueueItemStatus.IN_TRIAGE);
                assertThat(result.getCalledBy()).isEqualTo("earlier-actor");
                assertThat(result.getCalledAt()).isEqualTo(originalCallTime);
            }
        }

        @Test
        @DisplayName("updateItemStatus to COMPLETED sets completedAt timestamp")
        void statusToCompletedSetsTimestamp() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();
                QueueItemEntity item = new QueueItemEntity();
                item.setId(itemId);
                item.setQueueId(QUEUE_ID);
                item.setJourneyId("J-TEST-001");
                item.setStatus(QueueItemStatus.IN_SERVICE);

                when(queueItemRepository.findById(itemId)).thenReturn(Optional.of(item));
                when(queueItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                QueueItemEntity result = queueEngine.updateItemStatus(itemId, QueueItemStatus.COMPLETED);

                assertThat(result.getStatus()).isEqualTo(QueueItemStatus.COMPLETED);
                assertThat(result.getCompletedAt()).isNotNull();
            }
        }

        @Test
        @DisplayName("updateItemStatus to NO_SHOW transitions journey to NO_SHOW")
        void statusToNoShowTransitionsJourney() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();
                QueueItemEntity item = new QueueItemEntity();
                item.setId(itemId);
                item.setQueueId(QUEUE_ID);
                item.setJourneyId("J-TEST-001");
                item.setStatus(QueueItemStatus.CALLED);

                JourneyEntity journey = createJourney("J-TEST-001");

                when(queueItemRepository.findById(itemId)).thenReturn(Optional.of(item));
                when(journeyRepository.findByJourneyId("J-TEST-001")).thenReturn(Optional.of(journey));
                when(journeyStateMachine.transition(any(), any())).thenReturn(journey);
                when(queueItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                QueueItemEntity result = queueEngine.updateItemStatus(itemId, QueueItemStatus.NO_SHOW);

                assertThat(result.getStatus()).isEqualTo(QueueItemStatus.NO_SHOW);
                verify(journeyStateMachine).transition(journey, JourneyState.NO_SHOW);
            }
        }

        @Test
        @DisplayName("updateItemStatus writes a QUEUE_ITEM_UPDATED outbox event with previous and new status")
        void statusChangeEmitsOutboxEvent() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();
                QueueItemEntity item = new QueueItemEntity();
                item.setId(itemId);
                item.setQueueId(QUEUE_ID);
                item.setJourneyId("J-TEST-001");
                item.setPatientCpid("CPID-001");
                item.setTokenNumber(4);
                item.setStatus(QueueItemStatus.IN_SERVICE);

                when(queueItemRepository.findById(itemId)).thenReturn(Optional.of(item));
                when(queueItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                queueEngine.updateItemStatus(itemId, QueueItemStatus.COMPLETED);

                ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
                verify(outboxRepository).save(captor.capture());
                EventOutboxEntity event = captor.getValue();
                assertThat(event.getEventType()).isEqualTo("QUEUE_ITEM_UPDATED");
                assertThat(event.getAggregateId()).isEqualTo(itemId.toString());
                assertThat(event.getPayload())
                        .contains("\"previousStatus\":\"IN_SERVICE\"")
                        .contains("\"newStatus\":\"COMPLETED\"")
                        .contains("\"patientCpid\":\"CPID-001\"")
                        .contains("\"actorId\":\"" + ACTOR_ID + "\"");
            }
        }

        @Test
        @DisplayName("updateItemStatus throws when item not found")
        void updateStatusThrowsWhenNotFound() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();
                when(queueItemRepository.findById(itemId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> queueEngine.updateItemStatus(itemId, QueueItemStatus.COMPLETED))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Queue item not found");
            }
        }
    }

    @Nested
    @DisplayName("Transfer Operations")
    class TransferOperations {

        @Test
        @DisplayName("transferItem creates new item in target queue and marks old as TRANSFERRED")
        void transferCreatesNewItem() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();
                UUID targetQueueId = UUID.randomUUID();

                QueueItemEntity oldItem = new QueueItemEntity();
                oldItem.setId(itemId);
                oldItem.setQueueId(QUEUE_ID);
                oldItem.setJourneyId("J-TEST-001");
                oldItem.setPatientCpid("CPID-001");
                oldItem.setTenantId(TENANT_ID);
                oldItem.setFacilityId(FACILITY_ID);
                oldItem.setPriority(3);
                oldItem.setStatus(QueueItemStatus.WAITING);

                when(queueItemRepository.findById(itemId)).thenReturn(Optional.of(oldItem));
                when(queueItemRepository.findMaxTokenNumberByQueueId(targetQueueId)).thenReturn(10);
                when(queueItemRepository.save(any(QueueItemEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                QueueItemEntity newItem = queueEngine.transferItem(itemId, targetQueueId);

                assertThat(newItem).isNotNull();
                assertThat(newItem.getQueueId()).isEqualTo(targetQueueId);
                assertThat(newItem.getJourneyId()).isEqualTo("J-TEST-001");
                // patient_cpid is NOT NULL in pct_queue_items — a transfer that
                // drops it fails the DB constraint (regression guard)
                assertThat(newItem.getPatientCpid()).isEqualTo("CPID-001");
                assertThat(newItem.getTokenNumber()).isEqualTo(11);
                assertThat(newItem.getPriority()).isEqualTo(3);
                assertThat(newItem.getStatus()).isEqualTo(QueueItemStatus.WAITING);

                // Verify old item was marked as transferred
                assertThat(oldItem.getStatus()).isEqualTo(QueueItemStatus.TRANSFERRED);
                assertThat(oldItem.getCompletedAt()).isNotNull();

                // Two saves: one for old item, one for new item
                verify(queueItemRepository, times(2)).save(any(QueueItemEntity.class));
            }
        }
    }

    @Nested
    @DisplayName("Escalation Operations")
    class EscalationOperations {

        @Test
        @DisplayName("escalateItem bumps priority to at least 5, records reason/actor, emits QUEUE_ITEM_ESCALATED")
        void escalateInPlace() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();
                QueueItemEntity item = new QueueItemEntity();
                item.setId(itemId);
                item.setQueueId(QUEUE_ID);
                item.setJourneyId("J-TEST-001");
                item.setPatientCpid("CPID-001");
                item.setTokenNumber(7);
                item.setPriority(2);
                item.setStatus(QueueItemStatus.WAITING);

                when(queueItemRepository.findById(itemId)).thenReturn(Optional.of(item));
                when(queueItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                QueueItemEntity result = queueEngine.escalateItem(
                        itemId, "Deteriorating vitals in waiting area", null, null);

                assertThat(result.getPriority()).isEqualTo(5); // max(2 + 2, 5)
                assertThat(result.getStatus()).isEqualTo(QueueItemStatus.WAITING); // still callable
                assertThat(result.getEscalatedAt()).isNotNull();
                assertThat(result.getEscalatedBy()).isEqualTo(ACTOR_ID);
                assertThat(result.getEscalationReason()).isEqualTo("Deteriorating vitals in waiting area");

                ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
                verify(outboxRepository).save(captor.capture());
                assertThat(captor.getValue().getEventType()).isEqualTo("QUEUE_ITEM_ESCALATED");
                assertThat(captor.getValue().getPayload())
                        .contains("\"reason\":\"Deteriorating vitals in waiting area\"")
                        .contains("\"patientCpid\":\"CPID-001\"");
            }
        }

        @Test
        @DisplayName("escalateItem with target queue transfers the item and carries the escalation")
        void escalateWithTransfer() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();
                UUID targetQueueId = UUID.randomUUID();
                QueueItemEntity item = new QueueItemEntity();
                item.setId(itemId);
                item.setQueueId(QUEUE_ID);
                item.setJourneyId("J-TEST-001");
                item.setPatientCpid("CPID-001");
                item.setPriority(4);
                item.setStatus(QueueItemStatus.WAITING);

                when(queueItemRepository.findById(itemId)).thenReturn(Optional.of(item));
                when(queueItemRepository.findMaxTokenNumberByQueueId(targetQueueId)).thenReturn(0);
                when(queueItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                QueueItemEntity result = queueEngine.escalateItem(
                        itemId, "Needs emergency workspace", targetQueueId, null);

                assertThat(result.getQueueId()).isEqualTo(targetQueueId);
                assertThat(result.getPatientCpid()).isEqualTo("CPID-001");
                assertThat(result.getPriority()).isEqualTo(6); // max(4 + 2, 5)
                assertThat(result.getStatus()).isEqualTo(QueueItemStatus.WAITING);
                assertThat(result.getEscalatedAt()).isNotNull();
                assertThat(result.getEscalationReason()).isEqualTo("Needs emergency workspace");
                // Old item ended TRANSFERRED
                assertThat(item.getStatus()).isEqualTo(QueueItemStatus.TRANSFERRED);

                // Two outbox events: QUEUE_ITEM_TRANSFERRED + QUEUE_ITEM_ESCALATED
                ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
                verify(outboxRepository, times(2)).save(captor.capture());
                assertThat(captor.getAllValues())
                        .extracting(EventOutboxEntity::getEventType)
                        .containsExactly("QUEUE_ITEM_TRANSFERRED", "QUEUE_ITEM_ESCALATED");
            }
        }

        @Test
        @DisplayName("escalateItem rejects a blank reason")
        void escalateRequiresReason() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                assertThatThrownBy(() -> queueEngine.escalateItem(UUID.randomUUID(), "  ", null, null))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("reason is required");
            }
        }
    }
}
