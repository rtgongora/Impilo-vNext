package zw.gov.mohcc.impilo.costa.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import zw.gov.mohcc.impilo.costa.domain.repository.EncounterRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.IdempotencyRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.RefundRepository;
import zw.gov.mohcc.impilo.costa.service.*;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CostaMadiBloodConsumerTest {

    @Mock private BillService billService;
    @Mock private InpatientCostingService inpatientCostingService;
    @Mock private PaymentIntegrationService paymentService;
    @Mock private EncounterRepository encounterRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private IdempotencyRepository idempotencyRepository;
    @Mock private PaymentAllocationService paymentAllocationService;
    @Mock private ChargeRecordService chargeRecordService;
    @Mock private CostEventCaptureService costEventCaptureService;
    @Mock zw.gov.mohcc.impilo.costa.service.HealthWorkerExemptionEnrichment healthWorkerExemptionEnrichment;
    @Mock private Acknowledgment ack;

    private CostaEventConsumer consumer;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new CostaEventConsumer(billService, inpatientCostingService, paymentService,
                encounterRepository, refundRepository, idempotencyRepository, paymentAllocationService,
                chargeRecordService, costEventCaptureService, healthWorkerExemptionEnrichment, mapper);
    }

    @Test
    void bloodIssued_capturesCostSignal() {
        UUID tenant = UUID.randomUUID();
        String msg = """
                {"eventType":"BLOOD_ISSUED","tenantId":"%s","subjectId":"ORDER-1","eventId":"E1",
                 "payload":{"unitId":"UNIT-1","patientCpid":"CPID-1"}}
                """.formatted(tenant);

        consumer.onMadiBloodEvent(msg, ack);

        verify(costEventCaptureService).tryCaptureClinical(
                eq("BLOOD_ISSUED"), eq("MADI"), eq(tenant), eq("CPID-1"), any(), isNull(), any());
        verify(ack).acknowledge();
    }

    @Test
    void nonBillableBloodEvent_isIgnored() {
        String msg = "{\"eventType\":\"ORDER_CREATED\",\"tenantId\":\"" + UUID.randomUUID() + "\"}";
        consumer.onMadiBloodEvent(msg, ack);
        verify(costEventCaptureService, never()).tryCaptureClinical(any(), any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void duplicateBloodEvent_isIdempotent() {
        when(idempotencyRepository.existsById("MADI_BLOOD:E1")).thenReturn(true);
        String msg = """
                {"eventType":"BLOOD_ISSUED","tenantId":"%s","subjectId":"ORDER-1","eventId":"E1",
                 "payload":{"unitId":"UNIT-1"}}
                """.formatted(UUID.randomUUID());

        consumer.onMadiBloodEvent(msg, ack);

        verify(costEventCaptureService, never()).tryCaptureClinical(any(), any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }
}
