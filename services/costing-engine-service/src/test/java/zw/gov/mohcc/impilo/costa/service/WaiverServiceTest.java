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
import zw.gov.mohcc.impilo.costa.api.dto.GrantWaiverRequest;
import zw.gov.mohcc.impilo.costa.api.dto.WaiverDecisionRequest;
import zw.gov.mohcc.impilo.costa.api.dto.WaiverResponse;
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.WaiverEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.WaiverStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.WaiverRepository;
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
class WaiverServiceTest {

    @Mock private WaiverRepository repository;
    @Mock private EventOutboxRepository outboxRepository;
    @Captor private ArgumentCaptor<EventOutboxEntity> outboxCaptor;

    private WaiverService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WaiverService(repository, outboxRepository, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(
                tenantId, "clerk-1", "FACILITY_FINANCE", "PAYMENT",
                "dev", UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(repository.save(any(WaiverEntity.class))).thenAnswer(inv -> {
            WaiverEntity e = inv.getArgument(0);
            if (e.getWaiverId() == null) e.setWaiverId(UUID.randomUUID());
            return e;
        });
        lenient().when(repository.existsByTenantIdAndWaiverReference(any(), any())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void grant_full_setsGrantedAndNoValueEventYet() {
        WaiverResponse r = service.grant(new GrantWaiverRequest(
                "CPID-1", "enc-1", "BILL-1", null, null,
                "FULL", null, "USD", "HARDSHIP", "Indigent patient", "AUD-1"));

        assertEquals(WaiverStatus.GRANTED, r.status());
        assertTrue(r.waiverReference().startsWith("WVR-"));
        assertEquals("clerk-1", r.grantedBy());
        verify(outboxRepository, never()).save(any());  // value-event only on approval
    }

    @Test
    void grant_partial_requiresPositiveAmount() {
        var req = new GrantWaiverRequest("CPID-1", null, null, null, null,
                "PARTIAL", BigDecimal.ZERO, "USD", "GOODWILL", "x", null);
        assertThrows(IllegalArgumentException.class, () -> service.grant(req));
    }

    @Test
    void approve_grantedWaiver_emitsWaiverApplied() {
        WaiverEntity e = granted();
        when(repository.findByWaiverIdAndTenantId(e.getWaiverId(), tenantId)).thenReturn(Optional.of(e));

        WaiverResponse r = service.approve(e.getWaiverId(), new WaiverDecisionRequest("ok"));

        assertEquals(WaiverStatus.APPROVED, r.status());
        assertEquals("clerk-1", r.approvedBy());
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("WAIVER_APPLIED", outboxCaptor.getValue().getEventType());
        assertTrue(outboxCaptor.getValue().getPayload().contains("\"payerKind\":\"WAIVER\""));
        assertTrue(outboxCaptor.getValue().getPayload().contains("\"reversal\":false"));
    }

    @Test
    void approve_nonGranted_isRejected() {
        WaiverEntity e = granted();
        e.setStatus(WaiverStatus.APPROVED);
        when(repository.findByWaiverIdAndTenantId(e.getWaiverId(), tenantId)).thenReturn(Optional.of(e));
        UUID id = e.getWaiverId();
        var req = new WaiverDecisionRequest(null);
        assertThrows(IllegalArgumentException.class, () -> service.approve(id, req));
    }

    @Test
    void reject_grantedWaiver_terminalNoValueEvent() {
        WaiverEntity e = granted();
        when(repository.findByWaiverIdAndTenantId(e.getWaiverId(), tenantId)).thenReturn(Optional.of(e));

        WaiverResponse r = service.reject(e.getWaiverId(), new WaiverDecisionRequest("over policy cap"));

        assertEquals(WaiverStatus.REJECTED, r.status());
        assertEquals("over policy cap", r.rejectedReason());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void revoke_approvedWaiver_emitsReversal() {
        WaiverEntity e = granted();
        e.setStatus(WaiverStatus.APPROVED);
        when(repository.findByWaiverIdAndTenantId(e.getWaiverId(), tenantId)).thenReturn(Optional.of(e));

        WaiverResponse r = service.revoke(e.getWaiverId(), new WaiverDecisionRequest("granted in error"));

        assertEquals(WaiverStatus.REVOKED, r.status());
        verify(outboxRepository).save(outboxCaptor.capture());
        assertTrue(outboxCaptor.getValue().getPayload().contains("\"reversal\":true"));
        assertTrue(outboxCaptor.getValue().getPayload().contains("\"sourceServiceEvent\":\"WAIVER_REVOKED\""));
    }

    @Test
    void revoke_grantedWaiver_noReversalEvent() {
        WaiverEntity e = granted();  // GRANTED, never applied
        when(repository.findByWaiverIdAndTenantId(e.getWaiverId(), tenantId)).thenReturn(Optional.of(e));

        WaiverResponse r = service.revoke(e.getWaiverId(), new WaiverDecisionRequest("withdrawn"));

        assertEquals(WaiverStatus.REVOKED, r.status());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void revoke_terminalWaiver_isRejected() {
        WaiverEntity e = granted();
        e.setStatus(WaiverStatus.REVOKED);
        when(repository.findByWaiverIdAndTenantId(e.getWaiverId(), tenantId)).thenReturn(Optional.of(e));
        UUID id = e.getWaiverId();
        var req = new WaiverDecisionRequest(null);
        assertThrows(IllegalArgumentException.class, () -> service.revoke(id, req));
    }

    private WaiverEntity granted() {
        WaiverEntity e = new WaiverEntity();
        e.setWaiverId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setWaiverReference("WVR-TEST");
        e.setWaiverType(zw.gov.mohcc.impilo.costa.domain.enums.WaiverType.FULL);
        e.setCurrency("USD");
        e.setJustification("test");
        e.setStatus(WaiverStatus.GRANTED);
        e.setGrantedBy("clerk-1");
        return e;
    }
}
