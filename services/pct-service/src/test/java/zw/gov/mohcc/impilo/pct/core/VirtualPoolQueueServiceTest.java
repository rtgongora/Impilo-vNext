package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueItemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualPoolQueueServiceTest {

    @Mock private QueueRepository queueRepository;
    @Mock private QueueItemRepository queueItemRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private final UUID tenant = UUID.randomUUID();

    private VirtualPoolQueueService service() {
        lenient().when(queueItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        return new VirtualPoolQueueService(queueRepository, queueItemRepository, outboxRepository,
                new ObjectMapper());
    }

    private ReferralEntity referral(String urgency, String poolId) {
        ReferralEntity r = new ReferralEntity();
        r.setReferralId(UUID.randomUUID());
        r.setTenantId(tenant);
        r.setPatientCpid("CPID-1");
        r.setUrgency(urgency);
        r.setRoutingKind("POOL");
        r.setRoutingPoolId(poolId);
        return r;
    }

    private QueueEntity poolQueue(String poolId, String rules) {
        QueueEntity q = new QueueEntity();
        q.setQueueId(UUID.randomUUID());
        q.setTenantId(tenant);
        q.setVirtualPoolId(poolId);
        q.setSource(QueueEntity.SOURCE_TUSO);
        q.setSourceRef("vh-x:provincial-general");
        q.setName("Provincial General Teleconsult Queue");
        q.setRules(rules);
        q.setActive(true);
        return q;
    }

    @Test
    void submitEnqueuesPoolReferralWithUrgencyPriorityAndSlaDeadline() {
        ReferralEntity r = referral("URGENT", "PROVINCIAL_ZW_HA");
        when(queueItemRepository.findByTenantIdAndSourceRefAndStatusIn(eq(tenant), any(), anyCollection()))
                .thenReturn(List.of());
        when(queueRepository.findByTenantIdAndVirtualPoolIdAndActiveTrueOrderBySourceRefAsc(tenant, "PROVINCIAL_ZW_HA"))
                .thenReturn(List.of(poolQueue("PROVINCIAL_ZW_HA", "{\"slaMinutes\":30}")));
        when(queueItemRepository.findMaxTokenNumberByQueueId(any())).thenReturn(4);

        Optional<QueueItemEntity> item = service().enqueueForReferral(r);

        assertThat(item).isPresent();
        assertThat(item.get().getSourceRef()).isEqualTo(r.getReferralId().toString());
        assertThat(item.get().getJourneyId()).isNull();
        assertThat(item.get().getPriority()).isEqualTo(4); // URGENT
        assertThat(item.get().getTokenNumber()).isEqualTo(5);
        assertThat(item.get().getStatus()).isEqualTo(QueueItemStatus.WAITING);
        assertThat(item.get().getSlaDueAt())
                .isCloseTo(OffsetDateTime.now().plusMinutes(30), within(60, java.time.temporal.ChronoUnit.SECONDS));

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("QUEUE_ITEM_ENQUEUED");
    }

    @Test
    void queueWithoutSlaRulesEnqueuesWithoutDeadline() {
        ReferralEntity r = referral("ROUTINE", "PROVINCIAL_ZW_HA");
        when(queueItemRepository.findByTenantIdAndSourceRefAndStatusIn(eq(tenant), any(), anyCollection()))
                .thenReturn(List.of());
        when(queueRepository.findByTenantIdAndVirtualPoolIdAndActiveTrueOrderBySourceRefAsc(tenant, "PROVINCIAL_ZW_HA"))
                .thenReturn(List.of(poolQueue("PROVINCIAL_ZW_HA", "{}")));
        when(queueItemRepository.findMaxTokenNumberByQueueId(any())).thenReturn(0);

        Optional<QueueItemEntity> item = service().enqueueForReferral(r);

        assertThat(item).isPresent();
        assertThat(item.get().getSlaDueAt()).isNull();
        assertThat(item.get().getPriority()).isEqualTo(2); // ROUTINE
    }

    @Test
    void noMaterialisedQueueIsHonestDegradationNotAFailure() {
        ReferralEntity r = referral("ROUTINE", "UNMATERIALISED_POOL");
        when(queueItemRepository.findByTenantIdAndSourceRefAndStatusIn(eq(tenant), any(), anyCollection()))
                .thenReturn(List.of());
        when(queueRepository.findByTenantIdAndVirtualPoolIdAndActiveTrueOrderBySourceRefAsc(tenant, "UNMATERIALISED_POOL"))
                .thenReturn(List.of());

        Optional<QueueItemEntity> item = service().enqueueForReferral(r);

        assertThat(item).isEmpty();
        verify(queueItemRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void resubmitIsIdempotentWhileAnItemIsLive() {
        ReferralEntity r = referral("ROUTINE", "PROVINCIAL_ZW_HA");
        QueueItemEntity live = new QueueItemEntity();
        live.setId(UUID.randomUUID());
        live.setStatus(QueueItemStatus.WAITING);
        when(queueItemRepository.findByTenantIdAndSourceRefAndStatusIn(eq(tenant), any(), anyCollection()))
                .thenReturn(List.of(live));

        Optional<QueueItemEntity> item = service().enqueueForReferral(r);

        assertThat(item).contains(live);
        verify(queueItemRepository, never()).save(any());
    }

    @Test
    void nonPoolReferralIsNeverEnqueued() {
        ReferralEntity r = referral("ROUTINE", "PROVINCIAL_ZW_HA");
        r.setRoutingKind("PROVIDER");

        assertThat(service().enqueueForReferral(r)).isEmpty();
        verify(queueItemRepository, never()).save(any());
    }

    @Test
    void acceptCompletesTheLivePoolItemAndEmitsUpdate() {
        ReferralEntity r = referral("ROUTINE", "PROVINCIAL_ZW_HA");
        QueueItemEntity live = new QueueItemEntity();
        live.setId(UUID.randomUUID());
        live.setQueueId(UUID.randomUUID());
        live.setTenantId(tenant);
        live.setStatus(QueueItemStatus.WAITING);
        when(queueItemRepository.findByTenantIdAndSourceRefAndStatusIn(eq(tenant),
                eq(r.getReferralId().toString()), anyCollection())).thenReturn(List.of(live));

        int completed = service().completeForReferral(r, "provider-9");

        assertThat(completed).isEqualTo(1);
        assertThat(live.getStatus()).isEqualTo(QueueItemStatus.COMPLETED);
        assertThat(live.getCompletedAt()).isNotNull();
        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("QUEUE_ITEM_UPDATED");
    }

    @Test
    void urgencyPriorityMapping() {
        assertThat(VirtualPoolQueueService.priorityFromUrgency("EMERGENCY")).isEqualTo(5);
        assertThat(VirtualPoolQueueService.priorityFromUrgency("URGENT")).isEqualTo(4);
        assertThat(VirtualPoolQueueService.priorityFromUrgency("PRIORITY")).isEqualTo(3);
        assertThat(VirtualPoolQueueService.priorityFromUrgency("ROUTINE")).isEqualTo(2);
        assertThat(VirtualPoolQueueService.priorityFromUrgency(null)).isEqualTo(2);
    }
}
