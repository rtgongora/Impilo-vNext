package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.DeteriorationEscalationEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EarlyWarningScoreEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardPatientAlertEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.DeteriorationEscalationRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.WardPatientAlertRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InpatientSafetyServiceTest {

    @Mock private DeteriorationEscalationRepository escalationRepository;
    @Mock private WardPatientAlertRepository wardAlertRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private InpatientSafetyService service;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InpatientSafetyService(escalationRepository, wardAlertRepository,
                outboxRepository, new ObjectMapper(), 5, 15);
        TrustContextHolder.set(new TrustContext(tenant, "actor-nurse", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private EarlyWarningScoreEntity ews(int total, String risk) {
        EarlyWarningScoreEntity e = new EarlyWarningScoreEntity();
        e.setScoreId(UUID.randomUUID());
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-1");
        e.setTotalScore(total);
        e.setRiskLevel(risk);
        return e;
    }

    @Test
    void raiseFromEws_persistsEscalation_raisesAlert_andEmitsEvent() {
        when(escalationRepository.save(any())).thenAnswer(inv -> {
            DeteriorationEscalationEntity e = inv.getArgument(0);
            if (e.getEscalationId() == null) e.setEscalationId(UUID.randomUUID());
            return e;
        });

        UUID wardId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        DeteriorationEscalationEntity esc = service.raiseFromEws(ews(7, "HIGH"), wardId, facilityId);

        assertNotNull(esc.getResponseDueAt());
        assertEquals("RAISED", esc.getStatus());
        verify(wardAlertRepository).save(any(WardPatientAlertEntity.class));

        ArgumentCaptor<EventOutboxEntity> evt = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(evt.capture());
        assertEquals("inpatient.ews.escalation_triggered", evt.getValue().getEventType());
    }

    @Test
    void sweepOverdue_movesToOverdue_andRaisesRitoSafetySignal() {
        DeteriorationEscalationEntity esc = new DeteriorationEscalationEntity();
        esc.setEscalationId(UUID.randomUUID());
        esc.setTenantId(tenant);
        esc.setSubjectCpid("CPID-1");
        esc.setTotalScore(8);
        esc.setRiskLevel("HIGH");
        esc.setStatus("RAISED");
        esc.setResponseDueAt(OffsetDateTime.now().minusMinutes(1));

        when(escalationRepository.findByStatusInAndResponseDueAtBefore(any(), any()))
                .thenReturn(List.of(esc));
        when(escalationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.sweepOverdue();

        assertEquals("OVERDUE", esc.getStatus());
        assertNotNull(esc.getRitoCaseRef());

        ArgumentCaptor<EventOutboxEntity> evt = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(evt.capture());
        assertEquals("inpatient.safety.event_raised", evt.getValue().getEventType());
        assertTrue(evt.getValue().getPayloadJson().contains("DETERIORATION_RESPONSE_DELAY"));
    }

    @Test
    void respond_marksRespondedAndEmits() {
        UUID id = UUID.randomUUID();
        DeteriorationEscalationEntity esc = new DeteriorationEscalationEntity();
        esc.setEscalationId(id);
        esc.setTenantId(tenant);
        esc.setSubjectCpid("CPID-1");
        esc.setTotalScore(6);
        esc.setRiskLevel("MEDIUM");
        esc.setStatus("RAISED");
        esc.setResponseDueAt(OffsetDateTime.now().plusMinutes(10));

        when(escalationRepository.findById(id)).thenReturn(java.util.Optional.of(esc));
        when(escalationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeteriorationEscalationEntity out = service.respond(id, "Reviewed, sepsis bundle started");
        assertEquals("RESPONDED", out.getStatus());
        assertEquals("actor-nurse", out.getRespondedBy());
    }
}
