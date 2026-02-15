package zw.gov.mohcc.impilo.surv.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import zw.gov.mohcc.impilo.surv.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.SignalRepository;

@ExtendWith(MockitoExtension.class)
class SignalServiceTest {

    @Mock private SignalRepository signalRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private SignalService signalService;

    @BeforeEach
    void setUp() {
        signalService = new SignalService(signalRepository, outboxRepository);
    }

    @Nested
    @DisplayName("Create Signal")
    class CreateSignal {

        @Test
        void createsSignalWithCorrectFields() {
            UUID tenantId = UUID.randomUUID();
            SignalEntity saved = new SignalEntity();
            saved.setId(1L);
            saved.setTenantId(tenantId);
            saved.setName("Cholera Alert");
            saved.setEventType("DISEASE_NOTIFICATION");
            saved.setConditionField("diagnosis_code");
            saved.setThreshold(5);
            saved.setWindowHours(48);
            saved.setSeverity(Severity.HIGH);
            saved.setCreatedBy("epi-officer");

            when(signalRepository.save(any(SignalEntity.class))).thenReturn(saved);

            SignalEntity result = signalService.createSignal(
                    tenantId, "epi-officer", UUID.randomUUID(),
                    "Cholera Alert", "Detects cholera clusters",
                    "DISEASE_NOTIFICATION", "diagnosis_code",
                    5, 48, Severity.HIGH);

            assertThat(result.getName()).isEqualTo("Cholera Alert");
            assertThat(result.getThreshold()).isEqualTo(5);
            assertThat(result.getSeverity()).isEqualTo(Severity.HIGH);
        }

        @Test
        void publishesOutboxEventOnCreate() {
            UUID tenantId = UUID.randomUUID();
            SignalEntity saved = new SignalEntity();
            saved.setId(2L);
            saved.setTenantId(tenantId);
            saved.setName("Test Signal");
            saved.setEventType("LAB_RESULT");
            saved.setConditionField("result_code");
            saved.setThreshold(1);
            saved.setWindowHours(24);
            saved.setSeverity(Severity.MEDIUM);
            saved.setCreatedBy("admin");

            when(signalRepository.save(any())).thenReturn(saved);

            signalService.createSignal(tenantId, "admin", UUID.randomUUID(),
                    "Test Signal", null, "LAB_RESULT", "result_code",
                    1, 24, Severity.MEDIUM);

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo("SIGNAL_CREATED");
            assertThat(captor.getValue().getAggregateType()).isEqualTo("SIGNAL");
        }

        @Test
        void defaultsSeverityToMediumWhenNull() {
            UUID tenantId = UUID.randomUUID();
            ArgumentCaptor<SignalEntity> captor = ArgumentCaptor.forClass(SignalEntity.class);

            SignalEntity saved = new SignalEntity();
            saved.setId(3L);
            saved.setTenantId(tenantId);
            saved.setName("Default");
            saved.setEventType("TEST");
            saved.setConditionField("field");
            saved.setSeverity(Severity.MEDIUM);
            saved.setCreatedBy("admin");

            when(signalRepository.save(captor.capture())).thenReturn(saved);

            signalService.createSignal(tenantId, "admin", UUID.randomUUID(),
                    "Default", null, "TEST", "field", 1, 24, null);

            assertThat(captor.getValue().getSeverity()).isEqualTo(Severity.MEDIUM);
        }
    }

    @Nested
    @DisplayName("List Signals")
    class ListSignals {

        @Test
        void returnsAllSignalsForTenant() {
            UUID tenantId = UUID.randomUUID();
            when(signalRepository.findByTenantId(tenantId))
                    .thenReturn(List.of(new SignalEntity(), new SignalEntity()));

            List<SignalEntity> result = signalService.listSignals(tenantId);
            assertThat(result).hasSize(2);
        }
    }
}
