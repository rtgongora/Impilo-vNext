package zw.gov.mohcc.impilo.pct.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.integration.TusoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueEntity;
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

@ExtendWith(MockitoExtension.class)
class QueueMaterializationServiceTest {

    @Mock private TusoIntegration tuso;
    @Mock private QueueRepository queueRepository;
    @Mock private zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository outboxRepository;

    private QueueMaterializationService service() {
        return new QueueMaterializationService(tuso, queueRepository, outboxRepository,
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private final UUID tenant = UUID.randomUUID();
    private final UUID facility = UUID.randomUUID();

    private Map<String, Object> def(String ref, String name, String type, boolean active) {
        return Map.of("sourceRef", ref, "name", name, "queueType", type, "active", active);
    }

    @Test
    void reconcileCreatesMissingQueuesFromTuso() {
        when(tuso.getQueueDefinitions(any(), eq(facility))).thenReturn(List.of(
                def("sp-1", "Triage", "TRIAGE", true),
                def("sp-2", "Consultation", "CONSULTATION", true)));
        when(queueRepository.findByTenantIdAndFacilityIdAndSourceRef(eq(tenant), eq(facility), any()))
                .thenReturn(Optional.empty());
        when(queueRepository.findByTenantIdAndFacilityIdAndSource(tenant, facility, QueueEntity.SOURCE_TUSO))
                .thenReturn(List.of());

        var r = service().reconcileFacility(tenant, facility);

        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.created()).isEqualTo(2);
        assertThat(r.updated()).isZero();
        ArgumentCaptor<QueueEntity> captor = ArgumentCaptor.forClass(QueueEntity.class);
        verify(queueRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        QueueEntity first = captor.getAllValues().get(0);
        assertThat(first.getSource()).isEqualTo(QueueEntity.SOURCE_TUSO);
        assertThat(first.getSourceRef()).isEqualTo("sp-1");
        assertThat(first.getMaterializationStatus()).isEqualTo(QueueEntity.STATUS_MATERIALIZED);
        assertThat(first.getQueueId()).isNotNull();
        assertThat(first.getLastMaterializedAt()).isNotNull();
    }

    @Test
    void reconcileIsIdempotentOnReplayNoDuplicateQueues() {
        QueueEntity existing = new QueueEntity();
        existing.setQueueId(UUID.randomUUID());
        existing.setTenantId(tenant);
        existing.setFacilityId(facility);
        existing.setSource(QueueEntity.SOURCE_TUSO);
        existing.setSourceRef("sp-1");
        existing.setName("Old Name");
        when(tuso.getQueueDefinitions(any(), eq(facility))).thenReturn(List.of(def("sp-1", "Triage", "TRIAGE", true)));
        when(queueRepository.findByTenantIdAndFacilityIdAndSourceRef(tenant, facility, "sp-1"))
                .thenReturn(Optional.of(existing));
        when(queueRepository.findByTenantIdAndFacilityIdAndSource(tenant, facility, QueueEntity.SOURCE_TUSO))
                .thenReturn(List.of(existing));

        var r = service().reconcileFacility(tenant, facility);

        assertThat(r.created()).isZero();
        assertThat(r.updated()).isEqualTo(1);
        // Same queue id retained, metadata updated in place (no new row).
        UUID keptId = existing.getQueueId();
        ArgumentCaptor<QueueEntity> captor = ArgumentCaptor.forClass(QueueEntity.class);
        verify(queueRepository).save(captor.capture());
        assertThat(captor.getValue().getQueueId()).isEqualTo(keptId);
        assertThat(captor.getValue().getName()).isEqualTo("Triage");
    }

    @Test
    void reconcileUpdatesChangedQueueMetadata() {
        QueueEntity existing = new QueueEntity();
        existing.setQueueId(UUID.randomUUID());
        existing.setSource(QueueEntity.SOURCE_TUSO);
        existing.setSourceRef("sp-1");
        existing.setName("Triage");
        existing.setQueueType("TRIAGE");
        when(tuso.getQueueDefinitions(any(), eq(facility))).thenReturn(List.of(def("sp-1", "Triage Renamed", "FIFO", true)));
        when(queueRepository.findByTenantIdAndFacilityIdAndSourceRef(tenant, facility, "sp-1"))
                .thenReturn(Optional.of(existing));
        when(queueRepository.findByTenantIdAndFacilityIdAndSource(tenant, facility, QueueEntity.SOURCE_TUSO))
                .thenReturn(List.of(existing));

        service().reconcileFacility(tenant, facility);

        assertThat(existing.getName()).isEqualTo("Triage Renamed");
        assertThat(existing.getQueueType()).isEqualTo("FIFO");
    }

    @Test
    void tusoRemovedQueueIsRetiredNotDeleted() {
        QueueEntity orphan = new QueueEntity();
        orphan.setQueueId(UUID.randomUUID());
        orphan.setSource(QueueEntity.SOURCE_TUSO);
        orphan.setSourceRef("sp-gone");
        orphan.setActive(true);
        orphan.setMaterializationStatus(QueueEntity.STATUS_MATERIALIZED);
        // TUSO no longer lists sp-gone (returns empty but NON-null = genuine "no queues").
        when(tuso.getQueueDefinitions(any(), eq(facility))).thenReturn(List.of());
        when(queueRepository.findByTenantIdAndFacilityIdAndSource(tenant, facility, QueueEntity.SOURCE_TUSO))
                .thenReturn(List.of(orphan));

        var r = service().reconcileFacility(tenant, facility);

        assertThat(r.retired()).isEqualTo(1);
        assertThat(orphan.isActive()).isFalse();
        assertThat(orphan.getMaterializationStatus()).isEqualTo(QueueEntity.STATUS_RETIRED);
        verify(queueRepository).save(orphan);
        verify(queueRepository, never()).delete(any());
        verify(queueRepository, never()).deleteById(any());
    }

    @Test
    void nullTusoResponseFailsSafelyWithoutTouchingQueues() {
        when(tuso.getQueueDefinitions(any(), eq(facility))).thenReturn(null);

        var r = service().reconcileFacility(tenant, facility);

        assertThat(r.status()).isEqualTo("FAILED");
        verify(queueRepository, never()).save(any());
        verify(queueRepository, never()).findByTenantIdAndFacilityIdAndSource(any(), any(), any());
    }

    @Test
    void malformedDefinitionIsSkippedNotFatal() {
        when(tuso.getQueueDefinitions(any(), eq(facility))).thenReturn(List.of(
                Map.of("name", "No Ref Queue"),          // missing sourceRef
                def("sp-9", "Good Queue", "FIFO", true)));
        when(queueRepository.findByTenantIdAndFacilityIdAndSourceRef(tenant, facility, "sp-9"))
                .thenReturn(Optional.empty());
        when(queueRepository.findByTenantIdAndFacilityIdAndSource(tenant, facility, QueueEntity.SOURCE_TUSO))
                .thenReturn(List.of());

        var r = service().reconcileFacility(tenant, facility);

        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.created()).isEqualTo(1);
    }

    @Test
    void materializationStatusReportsSeedVsMaterialised() {
        QueueEntity seeded = new QueueEntity();
        seeded.setSource(QueueEntity.SOURCE_SEED);
        seeded.setMaterializationStatus(QueueEntity.STATUS_SEED_DEMO);
        QueueEntity mat = new QueueEntity();
        mat.setSource(QueueEntity.SOURCE_TUSO);
        mat.setMaterializationStatus(QueueEntity.STATUS_MATERIALIZED);
        mat.setLastMaterializedAt(java.time.OffsetDateTime.now());
        when(queueRepository.findByTenantIdAndFacilityId(tenant, facility)).thenReturn(List.of(seeded, mat));

        var summary = service().materializationStatus(tenant, facility);

        assertThat(summary.get("overall")).isEqualTo(QueueEntity.STATUS_MATERIALIZED);
        assertThat(summary.get("materialisedFromTuso")).isEqualTo(1L);
        assertThat(summary.get("seedDemoOnly")).isEqualTo(1L);
        assertThat(summary.get("lastMaterializedAt")).isNotNull();
    }
}
