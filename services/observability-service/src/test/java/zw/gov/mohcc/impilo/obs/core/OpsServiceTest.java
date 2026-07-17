package zw.gov.mohcc.impilo.obs.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
