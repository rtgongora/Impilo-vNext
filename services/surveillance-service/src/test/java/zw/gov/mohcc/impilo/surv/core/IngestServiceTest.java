package zw.gov.mohcc.impilo.surv.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
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
import zw.gov.mohcc.impilo.surv.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalHitEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.CaseRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.SignalHitRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.SignalRepository;

@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    @Mock private SignalRepository signalRepository;
    @Mock private SignalHitRepository signalHitRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private IngestService ingestService;

    @BeforeEach
    void setUp() {
        ingestService = new IngestService(signalRepository, signalHitRepository,
                caseRepository, outboxRepository);
    }

    @Nested
    @DisplayName("Event Ingestion")
    class EventIngestion {

        @Test
        void noHitsWhenNoMatchingSignals() {
            UUID tenantId = UUID.randomUUID();
            when(signalRepository.findByTenantIdAndEventTypeAndStatus(
                    tenantId, "UNKNOWN_EVENT", SignalStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());

            IngestService.IngestResult result = ingestService.ingest(
                    tenantId, "user-1", UUID.randomUUID(),
                    "UNKNOWN_EVENT", "{}", null, "idem-1");

            assertThat(result.signalsMatched()).isZero();
            assertThat(result.hitsRecorded()).isZero();
            assertThat(result.casesOpened()).isZero();
            verify(signalHitRepository, never()).save(any());
        }

        @Test
        void recordsHitForMatchingSignal() {
            UUID tenantId = UUID.randomUUID();
            SignalEntity signal = new SignalEntity();
            signal.setId(10L);
            signal.setName("Test Signal");
            signal.setEventType("DISEASE_NOTIFICATION");
            signal.setThreshold(10);
            signal.setSeverity(Severity.HIGH);

            when(signalRepository.findByTenantIdAndEventTypeAndStatus(
                    tenantId, "DISEASE_NOTIFICATION", SignalStatus.ACTIVE))
                    .thenReturn(List.of(signal));

            SignalHitEntity savedHit = new SignalHitEntity();
            savedHit.setId(100L);
            savedHit.setSignalId(10L);
            savedHit.setEventType("DISEASE_NOTIFICATION");
            when(signalHitRepository.save(any())).thenReturn(savedHit);

            IngestService.IngestResult result = ingestService.ingest(
                    tenantId, "user-1", UUID.randomUUID(),
                    "DISEASE_NOTIFICATION", "{\"code\":\"A00\"}", null, "idem-2");

            assertThat(result.signalsMatched()).isEqualTo(1);
            assertThat(result.hitsRecorded()).isEqualTo(1);
            assertThat(result.casesOpened()).isZero();
        }

        @Test
        void autoOpensCaseWhenThresholdIsOne() {
            UUID tenantId = UUID.randomUUID();
            SignalEntity signal = new SignalEntity();
            signal.setId(20L);
            signal.setName("Immediate Alert");
            signal.setEventType("CRITICAL_LAB");
            signal.setThreshold(1);
            signal.setSeverity(Severity.CRITICAL);

            when(signalRepository.findByTenantIdAndEventTypeAndStatus(
                    tenantId, "CRITICAL_LAB", SignalStatus.ACTIVE))
                    .thenReturn(List.of(signal));

            SignalHitEntity savedHit = new SignalHitEntity();
            savedHit.setId(200L);
            savedHit.setSignalId(20L);
            savedHit.setEventType("CRITICAL_LAB");
            when(signalHitRepository.save(any())).thenReturn(savedHit);

            CaseEntity savedCase = new CaseEntity();
            savedCase.setId(300L);
            savedCase.setTenantId(tenantId);
            savedCase.setCaseType("CRITICAL_LAB");
            savedCase.setTitle("Auto-case: Immediate Alert");
            savedCase.setSeverity(Severity.CRITICAL);
            savedCase.setStatus(CaseStatus.OPEN);
            savedCase.setOpenedBy("user-1");
            when(caseRepository.save(any())).thenReturn(savedCase);

            IngestService.IngestResult result = ingestService.ingest(
                    tenantId, "user-1", UUID.randomUUID(),
                    "CRITICAL_LAB", "{}", null, "idem-3");

            assertThat(result.casesOpened()).isEqualTo(1);
            verify(caseRepository).save(any());
        }

        @Test
        void publishesOutboxEventsForHitsAndCases() {
            UUID tenantId = UUID.randomUUID();
            SignalEntity signal = new SignalEntity();
            signal.setId(30L);
            signal.setName("Alert");
            signal.setEventType("EVENT_X");
            signal.setThreshold(1);
            signal.setSeverity(Severity.LOW);

            when(signalRepository.findByTenantIdAndEventTypeAndStatus(
                    tenantId, "EVENT_X", SignalStatus.ACTIVE))
                    .thenReturn(List.of(signal));

            SignalHitEntity savedHit = new SignalHitEntity();
            savedHit.setId(400L);
            savedHit.setSignalId(30L);
            savedHit.setEventType("EVENT_X");
            when(signalHitRepository.save(any())).thenReturn(savedHit);

            CaseEntity savedCase = new CaseEntity();
            savedCase.setId(500L);
            savedCase.setTenantId(tenantId);
            savedCase.setCaseType("EVENT_X");
            savedCase.setTitle("Auto-case: Alert");
            savedCase.setSeverity(Severity.LOW);
            savedCase.setStatus(CaseStatus.OPEN);
            savedCase.setOpenedBy("user-1");
            when(caseRepository.save(any())).thenReturn(savedCase);

            ingestService.ingest(tenantId, "user-1", UUID.randomUUID(),
                    "EVENT_X", "{}", null, "idem-4");

            ArgumentCaptor<EventOutboxEntity> captor =
                    ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());

            List<String> eventTypes = captor.getAllValues().stream()
                    .map(EventOutboxEntity::getEventType).toList();
            assertThat(eventTypes).contains("SIGNAL_HIT", "CASE_OPENED");
        }
    }
}
