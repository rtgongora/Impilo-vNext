package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.api.dto.FlagEmergencyDeferredChargeRequest;
import zw.gov.mohcc.impilo.costa.api.dto.LinkConfirmedPersonRequest;
import zw.gov.mohcc.impilo.costa.api.dto.ReconcileDeferredChargeRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.EmergencyDeferredChargeEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.ServiceAccessDecisionEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ReconciliationState;
import zw.gov.mohcc.impilo.costa.domain.enums.ServiceAccessStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.EmergencyDeferredChargeRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.ServiceAccessDecisionRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmergencyReconciliationServiceTest {

    @Mock private EmergencyDeferredChargeRepository repository;
    @Mock private ServiceAccessDecisionRepository accessDecisionRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Captor private ArgumentCaptor<EventOutboxEntity> outboxCaptor;

    private EmergencyReconciliationService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EmergencyReconciliationService(
                repository, accessDecisionRepository, outboxRepository, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(
                tenantId, "finance-clerk", "FACILITY_FINANCE", "PAYMENT",
                "dev", UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(repository.save(any(EmergencyDeferredChargeEntity.class)))
                .thenAnswer(inv -> {
                    EmergencyDeferredChargeEntity e = inv.getArgument(0);
                    if (e.getDeferredChargeId() == null) e.setDeferredChargeId(UUID.randomUUID());
                    return e;
                });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void flag_withoutDecision_persistsPendingAndEmitsDeferredValueEvent() {
        var resp = service.flag(new FlagEmergencyDeferredChargeRequest(
                null, null, "enc-1", null, "PROV-9",
                new BigDecimal("100.00"), "USD", "resus", "AUD-1"));

        assertEquals(ReconciliationState.PENDING, resp.reconciliationState());
        verify(outboxRepository).save(outboxCaptor.capture());
        EventOutboxEntity ev = outboxCaptor.getValue();
        assertEquals("EMERGENCY_DEFERRED_CHARGE", ev.getEventType());
        assertTrue(ev.getPayload().contains("\"deferred\":true"));
        assertTrue(ev.getPayload().contains("\"reconciliationState\":\"PENDING\""));
        assertTrue(ev.getPayload().contains("\"payerKind\":\"DEFERRED\""));
    }

    @Test
    void flag_withNonEmergencyDecision_isRejected() {
        UUID decisionId = UUID.randomUUID();
        ServiceAccessDecisionEntity d = new ServiceAccessDecisionEntity();
        d.setAccessStatus(ServiceAccessStatus.PAYMENT_REQUIRED_BEFORE_SERVICE);
        when(accessDecisionRepository.findByServiceAccessDecisionIdAndTenantId(decisionId, tenantId))
                .thenReturn(Optional.of(d));

        var req = new FlagEmergencyDeferredChargeRequest(
                decisionId, null, null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.flag(req));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void flag_withEmergencyDecision_inheritsContext() {
        UUID decisionId = UUID.randomUUID();
        ServiceAccessDecisionEntity d = new ServiceAccessDecisionEntity();
        d.setAccessStatus(ServiceAccessStatus.EMERGENCY_OVERRIDE);
        d.setEncounterId("enc-from-decision");
        d.setPatientCpid("PROV-FROM-DECISION");
        d.setTariffedPrice(new BigDecimal("88.50"));
        when(accessDecisionRepository.findByServiceAccessDecisionIdAndTenantId(decisionId, tenantId))
                .thenReturn(Optional.of(d));

        var resp = service.flag(new FlagEmergencyDeferredChargeRequest(
                decisionId, null, null, null, null, null, null, null, null));

        assertEquals("enc-from-decision", resp.encounterId());
        assertEquals("PROV-FROM-DECISION", resp.provisionalPersonRef());
        assertEquals(0, new BigDecimal("88.50").compareTo(resp.deferredAmount()));
    }

    @Test
    void reconcile_routesToClaim_marksReconciledAndEmitsEvent() {
        UUID id = UUID.randomUUID();
        EmergencyDeferredChargeEntity e = new EmergencyDeferredChargeEntity();
        e.setDeferredChargeId(id);
        e.setTenantId(tenantId);
        e.setReconciliationState(ReconciliationState.PENDING);
        e.setDeferredAmount(new BigDecimal("100.00"));
        when(repository.findByDeferredChargeIdAndTenantId(id, tenantId)).thenReturn(Optional.of(e));

        var resp = service.reconcile(id, new ReconcileDeferredChargeRequest("CLAIM", "CLAIM-PACK-1", "insured"));

        assertEquals(ReconciliationState.RECONCILED, resp.reconciliationState());
        assertEquals("CLAIM", resp.settlementRoute().name());
        verify(outboxRepository).save(outboxCaptor.capture());
        assertTrue(outboxCaptor.getValue().getPayload().contains("\"payerKind\":\"SCHEME\""));
        assertTrue(outboxCaptor.getValue().getPayload().contains("\"reconciliationState\":\"RECONCILED\""));
    }

    @Test
    void reconcile_alreadyReconciled_isRejected() {
        UUID id = UUID.randomUUID();
        EmergencyDeferredChargeEntity e = new EmergencyDeferredChargeEntity();
        e.setDeferredChargeId(id);
        e.setTenantId(tenantId);
        e.setReconciliationState(ReconciliationState.RECONCILED);
        when(repository.findByDeferredChargeIdAndTenantId(id, tenantId)).thenReturn(Optional.of(e));

        var req = new ReconcileDeferredChargeRequest("SETTLE", null, null);
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(id, req));
    }

    @Test
    void linkConfirmedPerson_setsConfirmedRefAndFlagsReconciled() {
        UUID id = UUID.randomUUID();
        EmergencyDeferredChargeEntity e = new EmergencyDeferredChargeEntity();
        e.setDeferredChargeId(id);
        e.setTenantId(tenantId);
        e.setProvisionalPersonRef("PROV-9");
        e.setReconciliationState(ReconciliationState.PENDING);
        when(repository.findByDeferredChargeIdAndTenantId(id, tenantId)).thenReturn(Optional.of(e));

        var resp = service.linkConfirmedPerson(id, new LinkConfirmedPersonRequest("CPID-REAL-1", "matched on phone"));

        assertEquals("CPID-REAL-1", resp.confirmedPersonRef());
        assertEquals("PROV-9", resp.provisionalPersonRef());
        assertTrue(resp.identityReconciled());
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }
}
