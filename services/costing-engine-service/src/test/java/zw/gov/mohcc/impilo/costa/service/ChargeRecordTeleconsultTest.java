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
}
