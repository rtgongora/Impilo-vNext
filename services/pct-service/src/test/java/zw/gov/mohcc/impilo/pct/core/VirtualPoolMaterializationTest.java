package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.integration.TusoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Virtual-pool materialisation: TUSO virtual-service queue definitions →
 * pct_queues rows scoped by virtual_pool_id (the routing seam target_ref).
 * Same invariants as facility materialisation: idempotent by sourceRef,
 * retire-never-delete, fail-safe on TUSO unavailability. Success emits
 * POOL_MATERIALIZED so TUSO can flip ACTIVATION_REQUESTED → ACTIVE.
 */
@ExtendWith(MockitoExtension.class)
class VirtualPoolMaterializationTest {

    @Mock private TusoIntegration tuso;
    @Mock private QueueRepository queueRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private final UUID tenant = UUID.randomUUID();

    private QueueMaterializationService service() {
        return new QueueMaterializationService(tuso, queueRepository, outboxRepository, new ObjectMapper());
    }

    private Map<String, Object> virtualService(String vsUid, String poolId) {
        return Map.of(
                "vsUid", vsUid,
                "lifecycleStatus", "ACTIVATION_REQUESTED",
                "routingSeams", List.of(Map.of(
                        "routingType", "SPECIALTY_POOL", "targetRef", poolId, "active", true)),
                "queueDefs", List.of(
                        Map.of("queueKey", "provincial-general", "name", "Provincial General Teleconsult Queue",
                                "defaultSlaMinutes", 30, "rules", Map.of("slaMinutes", 30)),
                        Map.of("queueKey", "district-escalation", "name", "District Escalation Queue",
                                "rules", Map.of())));
    }

    @Test
    void materialisesPoolQueuesFromTusoAndEmitsPoolMaterialized() {
        when(tuso.getVirtualServices(tenant)).thenReturn(List.of(virtualService("vh-x", "PROVINCIAL_ZW_HA")));
        when(queueRepository.findByTenantIdAndSourceRefAndVirtualPoolIdIsNotNull(eq(tenant), any()))
                .thenReturn(Optional.empty());
        when(queueRepository.findByTenantIdAndSourceAndVirtualPoolIdIsNotNull(tenant, QueueEntity.SOURCE_TUSO))
                .thenReturn(List.of());

        var results = service().reconcileVirtualPools(tenant);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("OK");
        assertThat(results.get(0).created()).isEqualTo(2);
        assertThat(results.get(0).poolId()).isEqualTo("PROVINCIAL_ZW_HA");

        ArgumentCaptor<QueueEntity> queues = ArgumentCaptor.forClass(QueueEntity.class);
        verify(queueRepository, org.mockito.Mockito.times(2)).save(queues.capture());
        QueueEntity first = queues.getAllValues().get(0);
        assertThat(first.getVirtualPoolId()).isEqualTo("PROVINCIAL_ZW_HA");
        assertThat(first.getFacilityId()).isNull();
        assertThat(first.getSourceRef()).isEqualTo("vh-x:provincial-general");
        assertThat(first.getSource()).isEqualTo(QueueEntity.SOURCE_TUSO);
        assertThat(first.getMaterializationStatus()).isEqualTo(QueueEntity.STATUS_MATERIALIZED);
        assertThat(first.getRules()).contains("\"slaMinutes\":30");

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("POOL_MATERIALIZED");
        assertThat(outbox.getValue().getAggregateId()).isEqualTo("vh-x");
    }

    @Test
    void replayIsIdempotentSameQueueIdKept() {
        QueueEntity existing = new QueueEntity();
        UUID keptId = UUID.randomUUID();
        existing.setQueueId(keptId);
        existing.setTenantId(tenant);
        existing.setVirtualPoolId("PROVINCIAL_ZW_HA");
        existing.setSource(QueueEntity.SOURCE_TUSO);
        existing.setSourceRef("vh-x:provincial-general");
        existing.setName("Old Name");
        Map<String, Object> vs = Map.of(
                "vsUid", "vh-x",
                "routingSeams", List.of(Map.of("routingType", "SPECIALTY_POOL",
                        "targetRef", "PROVINCIAL_ZW_HA", "active", true)),
                "queueDefs", List.of(Map.of("queueKey", "provincial-general", "name", "Renamed Queue")));
        when(tuso.getVirtualServices(tenant)).thenReturn(List.of(vs));
        when(queueRepository.findByTenantIdAndSourceRefAndVirtualPoolIdIsNotNull(tenant, "vh-x:provincial-general"))
                .thenReturn(Optional.of(existing));
        when(queueRepository.findByTenantIdAndSourceAndVirtualPoolIdIsNotNull(tenant, QueueEntity.SOURCE_TUSO))
                .thenReturn(List.of(existing));

        var results = service().reconcileVirtualPools(tenant);

        assertThat(results.get(0).created()).isZero();
        assertThat(results.get(0).updated()).isEqualTo(1);
        assertThat(existing.getQueueId()).isEqualTo(keptId);
        assertThat(existing.getName()).isEqualTo("Renamed Queue");
    }

    @Test
    void poolQueueNoLongerPublishedIsRetiredNeverDeleted() {
        QueueEntity orphan = new QueueEntity();
        orphan.setQueueId(UUID.randomUUID());
        orphan.setTenantId(tenant);
        orphan.setVirtualPoolId("SUSPENDED_POOL");
        orphan.setSource(QueueEntity.SOURCE_TUSO);
        orphan.setSourceRef("vh-gone:some-queue");
        orphan.setActive(true);
        orphan.setMaterializationStatus(QueueEntity.STATUS_MATERIALIZED);
        // TUSO now lists NO activatable virtual services (empty, non-null).
        when(tuso.getVirtualServices(tenant)).thenReturn(List.of());
        when(queueRepository.findByTenantIdAndSourceAndVirtualPoolIdIsNotNull(tenant, QueueEntity.SOURCE_TUSO))
                .thenReturn(List.of(orphan));

        service().reconcileVirtualPools(tenant);

        assertThat(orphan.isActive()).isFalse();
        assertThat(orphan.getMaterializationStatus()).isEqualTo(QueueEntity.STATUS_RETIRED);
        verify(queueRepository).save(orphan);
        verify(queueRepository, never()).delete(any());
    }

    @Test
    void nullTusoResponseFailsSafelyWithoutTouchingPoolQueues() {
        when(tuso.getVirtualServices(tenant)).thenReturn(null);

        var results = service().reconcileVirtualPools(tenant);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("FAILED");
        verify(queueRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void entityWithoutActiveSeamIsSkippedDefensively() {
        Map<String, Object> vs = Map.of(
                "vsUid", "vh-x",
                "routingSeams", List.of(Map.of("routingType", "SPECIALTY_POOL",
                        "targetRef", "P", "active", false)),
                "queueDefs", List.of(Map.of("queueKey", "q", "name", "Q")));
        when(tuso.getVirtualServices(tenant)).thenReturn(List.of(vs));
        when(queueRepository.findByTenantIdAndSourceAndVirtualPoolIdIsNotNull(tenant, QueueEntity.SOURCE_TUSO))
                .thenReturn(List.of());

        var results = service().reconcileVirtualPools(tenant);

        assertThat(results.get(0).status()).isEqualTo("SKIPPED");
        verify(queueRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }
}
