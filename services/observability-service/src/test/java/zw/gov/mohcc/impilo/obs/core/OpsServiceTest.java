package zw.gov.mohcc.impilo.obs.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.obs.persistence.entity.ClientEventEntity;
import zw.gov.mohcc.impilo.obs.persistence.entity.ServiceHeartbeatEntity;
import zw.gov.mohcc.impilo.obs.persistence.repository.ClientEventRepository;
import zw.gov.mohcc.impilo.obs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.obs.persistence.repository.ServiceHeartbeatRepository;

@ExtendWith(MockitoExtension.class)
class OpsServiceTest {

    @Mock private ServiceHeartbeatRepository heartbeatRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ClientEventRepository clientEventRepository;

    private OpsService opsService;

    @BeforeEach
    void setUp() {
        opsService = new OpsService(heartbeatRepository, outboxRepository, clientEventRepository);
    }

    private static ServiceHeartbeatEntity heartbeat(String service, String instance,
                                                    OffsetDateTime last, String rawStatus) {
        ServiceHeartbeatEntity hb = new ServiceHeartbeatEntity();
        hb.setServiceName(service);
        hb.setInstanceId(instance);
        hb.setTenantId(UUID.randomUUID());
        hb.setStatus(rawStatus);
        hb.setVersionTag("9.9.9");
        hb.setMetadata("{\"secret\":\"leak\"}");
        hb.setLastHeartbeat(last);
        return hb;
    }

    @Nested
    @DisplayName("Public service-status projection")
    class PublicStatusProjection {

        @Test
        @DisplayName("derives UP/STALE and exposes only serviceName + status + lastHeartbeat")
        void projectionIsDisclosureLimited() {
            OffsetDateTime now = OffsetDateTime.now();
            when(heartbeatRepository.findAll()).thenReturn(List.of(
                    heartbeat("fresh-svc", "inst-a", now.minusMinutes(1), "DEGRADED"),
                    heartbeat("stale-svc", "inst-b", now.minusMinutes(30), "UP")));

            PublicServiceStatusSummary summary = opsService.getPublicServiceStatus();

            assertThat(summary.staleThresholdMinutes()).isEqualTo(5);
            assertThat(summary.generatedAt()).isNotNull();
            assertThat(summary.services()).hasSize(2);

            PublicServiceStatusItem fresh = summary.services().stream()
                    .filter(s -> s.serviceName().equals("fresh-svc")).findFirst().orElseThrow();
            PublicServiceStatusItem stale = summary.services().stream()
                    .filter(s -> s.serviceName().equals("stale-svc")).findFirst().orElseThrow();

            // Liveness is DERIVED (never the raw stored status string, never invented states).
            assertThat(fresh.status()).isEqualTo("UP");
            assertThat(stale.status()).isEqualTo("STALE");
            assertThat(fresh.lastHeartbeat()).isNotNull();

            // The typed projection has exactly three accessor components — no instance
            // ids, tenant, version, metadata, or instance counts can be reached.
            assertThat(PublicServiceStatusItem.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .containsExactlyInAnyOrder("serviceName", "status", "lastHeartbeat");
            assertThat(PublicServiceStatusSummary.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .containsExactlyInAnyOrder("generatedAt", "staleThresholdMinutes", "services");
        }

        @Test
        @DisplayName("collapses multiple instances of one service into a single item (no instance count)")
        void collapsesInstances() {
            OffsetDateTime now = OffsetDateTime.now();
            when(heartbeatRepository.findAll()).thenReturn(List.of(
                    heartbeat("svc", "inst-1", now.minusMinutes(30), "UP"),
                    heartbeat("svc", "inst-2", now.minusMinutes(1), "UP")));

            PublicServiceStatusSummary summary = opsService.getPublicServiceStatus();

            assertThat(summary.services()).hasSize(1);
            // Any instance fresh => service UP.
            assertThat(summary.services().get(0).status()).isEqualTo("UP");
        }
    }

    @Nested
    @DisplayName("Client-event ingest")
    class ClientEventIngest {

        @Test
        @DisplayName("caps the batch at 50 events; extras are ignored")
        void capsBatch() {
            List<ClientEventCommand> events = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                events.add(new ClientEventCommand("/r/" + i, "CODE", null, null, 200, null));
            }

            int accepted = opsService.recordClientEvents(UUID.randomUUID(), events);

            assertThat(accepted).isEqualTo(50);
            verify(clientEventRepository, times(50)).save(any(ClientEventEntity.class));
        }

        @Test
        @DisplayName("truncates over-long route/code/correlation/request fields defensively")
        void truncatesOverLongFields() {
            String longRoute = "/".repeat(300);      // > 256
            String longCode = "C".repeat(100);        // > 64
            String longCorr = "X".repeat(100);        // > 64
            String longReq = "Y".repeat(100);         // > 64

            ClientEventCommand ev = new ClientEventCommand(
                    longRoute, longCode, longCorr, longReq, 500, "not-a-date");

            int accepted = opsService.recordClientEvents(
                    UUID.randomUUID(), List.of(ev));

            assertThat(accepted).isEqualTo(1);
            ArgumentCaptor<ClientEventEntity> captor = ArgumentCaptor.forClass(ClientEventEntity.class);
            verify(clientEventRepository).save(captor.capture());
            ClientEventEntity saved = captor.getValue();

            assertThat(saved.getRoute()).hasSize(256);
            assertThat(saved.getCode()).hasSize(64);
            assertThat(saved.getCorrelationId()).hasSize(64);
            assertThat(saved.getRequestId()).hasSize(64);
            assertThat(saved.getHttpStatus()).isEqualTo(500);
            // Unparseable timestamp is dropped defensively, not thrown.
            assertThat(saved.getOccurredAt()).isNull();
            assertThat(saved.getSource()).isEqualTo("CLIENT");
        }

        @Test
        @DisplayName("empty or null batch accepts zero and persists nothing")
        void emptyBatch() {
            assertThat(opsService.recordClientEvents(UUID.randomUUID(), List.of())).isZero();
            assertThat(opsService.recordClientEvents(UUID.randomUUID(), null)).isZero();
            verify(clientEventRepository, times(0)).save(any());
        }
    }
}
