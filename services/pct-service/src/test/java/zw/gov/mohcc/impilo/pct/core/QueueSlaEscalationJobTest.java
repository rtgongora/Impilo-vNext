package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueItemRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueSlaEscalationJobTest {

    @Mock private QueueItemRepository queueItemRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private QueueSlaEscalationJob job() {
        return new QueueSlaEscalationJob(queueItemRepository, outboxRepository, new ObjectMapper());
    }

    private QueueItemEntity breachedItem(int priority) {
        QueueItemEntity item = new QueueItemEntity();
        item.setId(UUID.randomUUID());
        item.setQueueId(UUID.randomUUID());
        item.setTenantId(UUID.randomUUID());
        item.setPatientCpid("CPID-1");
        item.setSourceRef(UUID.randomUUID().toString());
        item.setStatus(QueueItemStatus.WAITING);
        item.setPriority(priority);
        item.setSlaDueAt(OffsetDateTime.now().minusMinutes(10));
        return item;
    }

    @Test
    void breachedItemIsEscalatedAndBreachEventEmitted() {
        QueueItemEntity item = breachedItem(2);
        when(queueItemRepository.findTop100ByStatusAndSlaDueAtBeforeAndEscalatedAtIsNullOrderBySlaDueAtAsc(
                eq(QueueItemStatus.WAITING), any())).thenReturn(List.of(item));
        when(queueItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int escalated = job().escalateBreachedItems();

        assertThat(escalated).isEqualTo(1);
        assertThat(item.getPriority()).isEqualTo(5); // max(2+2, 5)
        assertThat(item.getEscalatedAt()).isNotNull();
        assertThat(item.getEscalatedBy()).isEqualTo(QueueSlaEscalationJob.SYSTEM_ACTOR);
        assertThat(item.getEscalationReason()).contains("SLA breached");
        // Status untouched: escalation is urgency + visibility, not a lifecycle transition.
        assertThat(item.getStatus()).isEqualTo(QueueItemStatus.WAITING);

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("QUEUE_SLA_BREACHED");
        assertThat(outbox.getValue().getPayload().toString()).contains(item.getSourceRef());
    }

    @Test
    void highPriorityBreachStillClimbs() {
        QueueItemEntity item = breachedItem(4);
        when(queueItemRepository.findTop100ByStatusAndSlaDueAtBeforeAndEscalatedAtIsNullOrderBySlaDueAtAsc(
                eq(QueueItemStatus.WAITING), any())).thenReturn(List.of(item));
        when(queueItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        job().escalateBreachedItems();

        assertThat(item.getPriority()).isEqualTo(6); // max(4+2, 5) — strictly increases
    }

    @Test
    void nothingBreachedNothingTouched() {
        when(queueItemRepository.findTop100ByStatusAndSlaDueAtBeforeAndEscalatedAtIsNullOrderBySlaDueAtAsc(
                eq(QueueItemStatus.WAITING), any())).thenReturn(List.of());

        assertThat(job().escalateBreachedItems()).isZero();
        verify(queueItemRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }
}
