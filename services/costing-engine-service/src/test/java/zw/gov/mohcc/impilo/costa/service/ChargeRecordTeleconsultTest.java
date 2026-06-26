package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargeRecordEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.ChargeRecordRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargeRecordTeleconsultTest {

    @Mock private ChargeRecordRepository chargeRecordRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Captor private ArgumentCaptor<ChargeRecordEntity> chargeCaptor;

    private ChargeRecordService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ChargeRecordService(chargeRecordRepository, outboxRepository, mapper);
    }

    private JsonNode payload(String status) throws Exception {
        return mapper.readTree("""
                {
                  "id": "REF-1",
                  "patientCpid": "CPID-1",
                  "providerId": "PROV-1",
                  "encounterId": "ENC-1",
                  "specialty": "CARDIOLOGY",
                  "modality": "VIDEO",
                  "facilityId": "%s",
                  "status": "%s"
                }
                """.formatted(UUID.randomUUID(), status));
    }

    @Test
    void completed_createsTeleconsultChargeAndValueEvent() throws Exception {
        when(chargeRecordRepository.existsByTenantIdAndSourceTypeAndSourceRef(
                eq(tenantId), eq("TELECONSULT_COMPLETED"), eq("REF-1"))).thenReturn(false);

        service.ingestTeleconsultCompleted(payload("COMPLETED"), tenantId);

        verify(chargeRecordRepository).save(chargeCaptor.capture());
        ChargeRecordEntity c = chargeCaptor.getValue();
        assertEquals("TELECONSULT", c.getChargeType());
        assertEquals("TELECONSULT_COMPLETED", c.getSourceType());
        assertEquals("REF-1", c.getSourceRef());
        assertEquals("CPID-1", c.getClientRef());
        assertEquals("PROV-1", c.getProviderRef());
        assertTrue(c.getChargeCode().contains("CARDIOLOGY"));
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void nonCompletedStatus_doesNotCharge() throws Exception {
        service.ingestTeleconsultCompleted(payload("ACCEPTED"), tenantId);
        verify(chargeRecordRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void duplicate_isIdempotent() throws Exception {
        when(chargeRecordRepository.existsByTenantIdAndSourceTypeAndSourceRef(
                eq(tenantId), eq("TELECONSULT_COMPLETED"), eq("REF-1"))).thenReturn(true);

        service.ingestTeleconsultCompleted(payload("COMPLETED"), tenantId);

        verify(chargeRecordRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void missingReferralId_isSkipped() throws Exception {
        JsonNode p = mapper.readTree("{\"status\":\"COMPLETED\",\"patientCpid\":\"CPID-1\"}");
        service.ingestTeleconsultCompleted(p, tenantId);
        verify(chargeRecordRepository, never()).save(any());
    }

    private JsonNode referralShaped() throws Exception {
        // referral-complete event: payload id IS the referralId.
        return mapper.readTree("""
                {"id":"REF-1","status":"COMPLETED","patientCpid":"CPID-1",
                 "encounterId":"ENC-1","specialty":"CARDIOLOGY"}
                """);
    }

    private JsonNode sessionShaped() throws Exception {
        // session-end event: payload id is the SESSION id, referralId carried separately.
        return mapper.readTree("""
                {"id":"SESS-1","referralId":"REF-1","status":"COMPLETED","patientCpid":"CPID-1",
                 "encounterId":"ENC-1","specialty":"CARDIOLOGY"}
                """);
    }

    /**
     * C1 regression: both lifecycle events for ONE teleconsult must collapse to exactly one
     * charge. Both events resolve to the same referralId anchor (REF-1) → second is deduped.
     * BEFORE the fix the referral event anchored on REF-1 and the session event on SESS-1,
     * so BOTH billed (double-charge).
     */
    @Test
    void referralThenSession_chargesExactlyOnce() throws Exception {
        // Stateful dedup: first save for REF-1 wins, subsequent existence check returns true.
        java.util.Set<String> seen = new java.util.HashSet<>();
        when(chargeRecordRepository.existsByTenantIdAndSourceTypeAndSourceRef(
                eq(tenantId), eq("TELECONSULT_COMPLETED"), eq("REF-1")))
                .thenAnswer(inv -> seen.contains("REF-1"));
        when(chargeRecordRepository.save(any(ChargeRecordEntity.class)))
                .thenAnswer(inv -> {
                    ChargeRecordEntity c = inv.getArgument(0);
                    seen.add(c.getSourceRef());
                    return c;
                });

        service.ingestTeleconsultCompleted(referralShaped(), tenantId);
        service.ingestTeleconsultCompleted(sessionShaped(), tenantId);

        verify(chargeRecordRepository, times(1)).save(chargeCaptor.capture());
        assertEquals("REF-1", chargeCaptor.getValue().getSourceRef());
        verify(outboxRepository, times(1)).save(any(EventOutboxEntity.class));
    }

    /** Same as above but session event arrives first — still exactly one charge. */
    @Test
    void sessionThenReferral_chargesExactlyOnce() throws Exception {
        java.util.Set<String> seen = new java.util.HashSet<>();
        when(chargeRecordRepository.existsByTenantIdAndSourceTypeAndSourceRef(
                eq(tenantId), eq("TELECONSULT_COMPLETED"), eq("REF-1")))
                .thenAnswer(inv -> seen.contains("REF-1"));
        when(chargeRecordRepository.save(any(ChargeRecordEntity.class)))
                .thenAnswer(inv -> {
                    ChargeRecordEntity c = inv.getArgument(0);
                    seen.add(c.getSourceRef());
                    return c;
                });

        service.ingestTeleconsultCompleted(sessionShaped(), tenantId);
        service.ingestTeleconsultCompleted(referralShaped(), tenantId);

        verify(chargeRecordRepository, times(1)).save(any(ChargeRecordEntity.class));
        verify(outboxRepository, times(1)).save(any(EventOutboxEntity.class));
    }
}
