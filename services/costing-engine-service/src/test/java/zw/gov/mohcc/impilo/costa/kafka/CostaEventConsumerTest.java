package zw.gov.mohcc.impilo.costa.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.support.Acknowledgment;
import zw.gov.mohcc.impilo.costa.domain.entity.EncounterEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.EncounterStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.EncounterType;
import zw.gov.mohcc.impilo.costa.domain.repository.EncounterRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.IdempotencyRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.RefundRepository;
import zw.gov.mohcc.impilo.costa.service.BillService;
import zw.gov.mohcc.impilo.costa.service.ChargeRecordService;
import zw.gov.mohcc.impilo.costa.service.CostEventCaptureService;
import zw.gov.mohcc.impilo.costa.service.InpatientCostingService;
import zw.gov.mohcc.impilo.costa.service.PaymentAllocationService;
import zw.gov.mohcc.impilo.costa.service.PaymentIntegrationService;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostaEventConsumerTest {

    @Mock BillService billService;
    @Mock InpatientCostingService inpatientCostingService;
    @Mock PaymentIntegrationService paymentService;
    @Mock EncounterRepository encounterRepository;
    @Mock RefundRepository refundRepository;
    @Mock IdempotencyRepository idempotencyRepository;
    @Mock PaymentAllocationService paymentAllocationService;
    @Mock ChargeRecordService chargeRecordService;
    @Mock CostEventCaptureService costEventCaptureService;
    @Mock Acknowledgment ack;

    CostaEventConsumer consumer;

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FACILITY = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        consumer = new CostaEventConsumer(
                billService, inpatientCostingService, paymentService,
                encounterRepository, refundRepository, idempotencyRepository,
                paymentAllocationService, chargeRecordService, costEventCaptureService,
                new ObjectMapper());
    }

    private String teleconsultMessage() {
        return "{"
                + "\"eventId\":\"ref-1:TELECONSULT_COMPLETED\","
                + "\"tenantId\":\"" + TENANT + "\","
                + "\"referralId\":\"ref-1\","
                + "\"patientCpid\":\"CPID-1\","
                + "\"encounterId\":\"ENC-1\","
                + "\"facilityId\":\"" + FACILITY + "\","
                + "\"specialty\":\"CARDIOLOGY\","
                + "\"modality\":\"virtual\","
                + "\"sourceServiceEvent\":\"TELECONSULT_COMPLETED\""
                + "}";
    }

    @Test
    void onTeleconsultValue_createsEncounterAndRaisesCharge() {
        // not yet processed; no existing COSTA encounter resolvable
        when(idempotencyRepository.existsById(any())).thenReturn(false);
        when(encounterRepository.findByPctJourneyId(any())).thenReturn(Optional.empty());
        when(encounterRepository.findById(any())).thenReturn(Optional.empty());
        when(encounterRepository.findByPatientCpidOrderByOpenedAtDesc(any()))
                .thenReturn(Collections.emptyList());

        consumer.onTeleconsultValue(teleconsultMessage(), ack);

        // a COSTA encounter is created for the teleconsult
        ArgumentCaptor<EncounterEntity> encCaptor = ArgumentCaptor.forClass(EncounterEntity.class);
        verify(encounterRepository).save(encCaptor.capture());
        EncounterEntity created = encCaptor.getValue();
        assertEquals(TENANT, created.getTenantId());
        assertEquals(FACILITY, created.getFacilityId());
        assertEquals("CPID-1", created.getPatientCpid());
        assertEquals("ENC-1", created.getPctJourneyId());
        assertEquals(EncounterType.OUTPATIENT, created.getEncounterType());
        assertEquals(EncounterStatus.OPEN, created.getStatus());

        // the teleconsult value-trigger is priced into a CHARGE_CREATED
        verify(chargeRecordService).ingestTeleconsultCompleted(any(JsonNode.class));
        verify(costEventCaptureService).tryCaptureClinical(
                eq("TELECONSULT_COMPLETED"), eq("PCT"), eq(TENANT), eq("CPID-1"),
                any(), any(), any());

        // idempotency recorded and the message acknowledged
        verify(idempotencyRepository).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void onTeleconsultValue_isIdempotent_whenAlreadyProcessed() {
        when(idempotencyRepository.existsById(eq("TELECONSULT:ref-1:TELECONSULT_COMPLETED")))
                .thenReturn(true);

        consumer.onTeleconsultValue(teleconsultMessage(), ack);

        verify(chargeRecordService, never()).ingestTeleconsultCompleted(any());
        verify(encounterRepository, never()).save(any());
        verify(ack).acknowledge();
    }
}
