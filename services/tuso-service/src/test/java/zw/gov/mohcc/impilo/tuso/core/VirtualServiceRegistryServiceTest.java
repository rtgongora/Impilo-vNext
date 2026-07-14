package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.VirtualServiceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.VirtualServiceHistoryEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.VirtualServiceQueueDefEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.VirtualServiceRoutingSeamEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.VirtualServiceHistoryRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.VirtualServiceRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualServiceRegistryServiceTest {

    @Mock private VirtualServiceRepository virtualServiceRepository;
    @Mock private VirtualServiceHistoryRepository historyRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID otherTenant = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final TrustContext ctx = new TrustContext(tenantId, "registry-admin", "REGISTRY_ADMIN",
            "CONFIGURATION", null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);

    private VirtualServiceRegistryService service() {
        lenient().when(virtualServiceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        return new VirtualServiceRegistryService(virtualServiceRepository, historyRepository,
                outboxRepository, new ObjectMapper());
    }

    private VirtualServiceEntity entity(String vsUid, boolean withActiveSeam, boolean withQueueDef) {
        VirtualServiceEntity e = new VirtualServiceEntity();
        e.setId(7L);
        e.setVsUid(vsUid);
        e.setTenantId(tenantId);
        e.setName("Test Virtual Hospital");
        e.setLevel("PROVINCIAL");
        e.setOperatingModel("NETWORKED");
        if (withActiveSeam) {
            VirtualServiceRoutingSeamEntity seam = new VirtualServiceRoutingSeamEntity();
            seam.setVirtualService(e);
            seam.setRoutingType("SPECIALTY_POOL");
            seam.setTargetRef("PROVINCIAL_ZW_HA");
            seam.setActive(true);
            e.getRoutingSeams().add(seam);
        }
        if (withQueueDef) {
            VirtualServiceQueueDefEntity def = new VirtualServiceQueueDefEntity();
            def.setVirtualService(e);
            def.setQueueKey("provincial-general");
            def.setName("Provincial General Teleconsult Queue");
            e.getQueueDefs().add(def);
        }
        return e;
    }

    private void known(VirtualServiceEntity e) {
        when(virtualServiceRepository.findByTenantIdAndVsUid(tenantId, e.getVsUid())).thenReturn(Optional.of(e));
    }

    // ------------------------------------------------------ activation matrix

    @Test
    void activateRefusedWithoutActiveSeam() {
        VirtualServiceEntity e = entity("vh-x", false, true);
        known(e);

        assertThatThrownBy(() -> service().activate(ctx, "vh-x"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no active routing seam");
        assertThat(e.getLifecycleStatus()).isEqualTo(VirtualServiceEntity.LIFECYCLE_CONFIGURED);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void activateRefusedWithInactiveSeamOnly() {
        VirtualServiceEntity e = entity("vh-x", true, true);
        e.getRoutingSeams().get(0).setActive(false);
        known(e);

        assertThatThrownBy(() -> service().activate(ctx, "vh-x"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no active routing seam");
    }

    @Test
    void activateRefusedWithoutQueueDefinitions() {
        VirtualServiceEntity e = entity("vh-x", true, false);
        known(e);

        assertThatThrownBy(() -> service().activate(ctx, "vh-x"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no queue definitions");
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void happyActivationMovesToActivationRequestedAndEmitsOutbox() {
        VirtualServiceEntity e = entity("vh-x", true, true);
        known(e);

        Map<String, Object> view = service().activate(ctx, "vh-x");

        // Fail-closed: requested, NOT active — only PCT materialisation confirmation flips ACTIVE.
        assertThat(view.get("lifecycleStatus")).isEqualTo(VirtualServiceEntity.LIFECYCLE_ACTIVATION_REQUESTED);
        assertThat(e.getActivatedBy()).isEqualTo("registry-admin");

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo(VirtualServiceRegistryService.EVENT_ACTIVATED);
        assertThat(outbox.getValue().getAggregateType()).isEqualTo("VIRTUAL_SERVICE");
        assertThat(outbox.getValue().getPayload())
                .containsEntry("vsUid", "vh-x")
                .containsEntry("tenantId", tenantId.toString())
                .containsEntry("lifecycleStatus", VirtualServiceEntity.LIFECYCLE_ACTIVATION_REQUESTED);
        // Outbox hygiene: null pod/idempotency values poison the publisher drain.
        assertThat(outbox.getValue().getPodId()).isNotNull();
        assertThat(outbox.getValue().getIdempotencyKey()).isNotNull();

        ArgumentCaptor<VirtualServiceHistoryEntity> history = ArgumentCaptor.forClass(VirtualServiceHistoryEntity.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getChangeType()).isEqualTo("ACTIVATE");
        assertThat(history.getValue().getNewValue()).isEqualTo(VirtualServiceEntity.LIFECYCLE_ACTIVATION_REQUESTED);
    }

    @Test
    void activateIsIdempotentWhenAlreadyRequested() {
        VirtualServiceEntity e = entity("vh-x", true, true);
        e.setLifecycleStatus(VirtualServiceEntity.LIFECYCLE_ACTIVATION_REQUESTED);
        known(e);

        Map<String, Object> view = service().activate(ctx, "vh-x");

        assertThat(view.get("lifecycleStatus")).isEqualTo(VirtualServiceEntity.LIFECYCLE_ACTIVATION_REQUESTED);
        verify(outboxRepository, never()).save(any());
    }

    // ------------------------------------------------------ ACTIVE flip

    @Test
    void materializationConfirmationFlipsRequestedToActiveOnly() {
        VirtualServiceEntity e = entity("vh-x", true, true);
        e.setLifecycleStatus(VirtualServiceEntity.LIFECYCLE_ACTIVATION_REQUESTED);
        known(e);

        assertThat(service().markActiveFromMaterialization(tenantId, "vh-x", "pct:pool-materialization")).isTrue();
        assertThat(e.getLifecycleStatus()).isEqualTo(VirtualServiceEntity.LIFECYCLE_ACTIVE);

        // Replay is a no-op (already ACTIVE) — the flip must never loop or overstate.
        assertThat(service().markActiveFromMaterialization(tenantId, "vh-x", "pct:pool-materialization")).isFalse();
    }

    @Test
    void materializationConfirmationIgnoresConfiguredEntities() {
        VirtualServiceEntity e = entity("vh-x", true, true);
        known(e);

        assertThat(service().markActiveFromMaterialization(tenantId, "vh-x", null)).isFalse();
        assertThat(e.getLifecycleStatus()).isEqualTo(VirtualServiceEntity.LIFECYCLE_CONFIGURED);
    }

    // ------------------------------------------------------ tenant isolation

    @Test
    void tenantIsolationOnReadsAndWrites() {
        when(virtualServiceRepository.findByTenantIdAndVsUid(otherTenant, "vh-x")).thenReturn(Optional.empty());
        VirtualServiceRegistryService svc = new VirtualServiceRegistryService(
                virtualServiceRepository, historyRepository, outboxRepository, new ObjectMapper());
        TrustContext foreign = new TrustContext(otherTenant, "intruder", "REGISTRY_ADMIN",
                "CONFIGURATION", null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);

        assertThatThrownBy(() -> svc.getByUid(otherTenant, "vh-x"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> svc.activate(foreign, "vh-x"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not found");
        verify(virtualServiceRepository, never()).save(any());
    }

    // ------------------------------------------------------ governed PATCH

    @Test
    void patchSeamActivationRecordsHistoryAndEmitsUpdatedEvent() {
        VirtualServiceEntity e = entity("vh-x", true, true);
        e.getRoutingSeams().get(0).setActive(false);
        known(e);

        Map<String, Object> view = service().patch(ctx, "vh-x", Map.of("routingSeams", List.of(
                Map.of("routingType", "SPECIALTY_POOL", "targetRef", "PROVINCIAL_ZW_HA", "active", true))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> seams = (List<Map<String, Object>>) view.get("routingSeams");
        assertThat(seams.get(0).get("active")).isEqualTo(true);

        ArgumentCaptor<VirtualServiceHistoryEntity> history = ArgumentCaptor.forClass(VirtualServiceHistoryEntity.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getChangeType()).isEqualTo("SEAM_UPDATE");
        assertThat(history.getValue().getChangedBy()).isEqualTo("registry-admin");

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo(VirtualServiceRegistryService.EVENT_UPDATED);
    }

    @Test
    void patchQueueDefSlaKeepsRulesDocumentInStepForPct() {
        VirtualServiceEntity e = entity("vh-x", true, true);
        known(e);

        Map<String, Object> view = service().patch(ctx, "vh-x", Map.of("queueDefs", List.of(
                Map.of("queueKey", "provincial-general", "defaultSlaMinutes", 30))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> defs = (List<Map<String, Object>>) view.get("queueDefs");
        assertThat(defs.get(0).get("defaultSlaMinutes")).isEqualTo(30);
        @SuppressWarnings("unchecked")
        Map<String, Object> rules = (Map<String, Object>) defs.get(0).get("rules");
        assertThat(rules).containsEntry("slaMinutes", 30);
        assertThat(defs.get(0).get("sourceRef")).isEqualTo("vh-x:provincial-general");
        verify(outboxRepository).save(any());
    }

    @Test
    void addSeamRejectsDuplicateAndRecordsHistoryOnSuccess() {
        VirtualServiceEntity e = entity("vh-x", true, true);
        known(e);
        VirtualServiceRegistryService svc = service();

        assertThatThrownBy(() -> svc.addSeam(ctx, "vh-x", Map.of(
                "routingType", "SPECIALTY_POOL", "targetRef", "PROVINCIAL_ZW_HA")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("already exists");

        Map<String, Object> view = svc.addSeam(ctx, "vh-x", Map.of(
                "routingType", "SPECIALTY_POOL", "targetRef", "PROVINCIAL_ZW_HA_OVERFLOW", "active", true));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> seams = (List<Map<String, Object>>) view.get("routingSeams");
        assertThat(seams).hasSize(2);
        verify(historyRepository).save(argThatHistory("SEAM_ADD"));
    }

    private static VirtualServiceHistoryEntity argThatHistory(String changeType) {
        return org.mockito.ArgumentMatchers.argThat(h -> h != null && changeType.equals(h.getChangeType()));
    }
}
