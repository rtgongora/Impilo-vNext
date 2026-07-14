package zw.gov.mohcc.impilo.costa.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import zw.gov.mohcc.impilo.costa.api.finance.FailedMoneyEventController;
import zw.gov.mohcc.impilo.costa.domain.entity.FailedMoneyEventEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.EncounterRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.FailedMoneyEventRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.IdempotencyRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.RefundRepository;
import zw.gov.mohcc.impilo.costa.service.BillService;
import zw.gov.mohcc.impilo.costa.service.ChargeRecordService;
import zw.gov.mohcc.impilo.costa.service.CostEventCaptureService;
import zw.gov.mohcc.impilo.costa.service.InpatientCostingService;
import zw.gov.mohcc.impilo.costa.service.PaymentAllocationService;
import zw.gov.mohcc.impilo.costa.service.PaymentIntegrationService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The money-event no-drop contract: a processing failure must NOT be acked away
 * (bounded retry then costa.money.dlq), dead letters are persisted for ops, and
 * replay re-publishes the original payload onto the original topic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoneyEventDlqTest {

    @Mock BillService billService;
    @Mock InpatientCostingService inpatientCostingService;
    @Mock PaymentIntegrationService paymentService;
    @Mock EncounterRepository encounterRepository;
    @Mock RefundRepository refundRepository;
    @Mock IdempotencyRepository idempotencyRepository;
    @Mock PaymentAllocationService paymentAllocationService;
    @Mock ChargeRecordService chargeRecordService;
    @Mock CostEventCaptureService costEventCaptureService;
    @Mock zw.gov.mohcc.impilo.costa.service.HealthWorkerExemptionEnrichment healthWorkerExemptionEnrichment;
    @Mock Acknowledgment ack;
    @Mock FailedMoneyEventRepository failedRepo;
    @Mock KafkaTemplate<String, String> kafkaTemplate;

    CostaEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CostaEventConsumer(
                billService, inpatientCostingService, paymentService,
                encounterRepository, refundRepository, idempotencyRepository,
                paymentAllocationService, chargeRecordService, costEventCaptureService,
                healthWorkerExemptionEnrichment, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void paymentStatusChanged_processingFailure_rethrowsAndNeverAcks() {
        when(idempotencyRepository.existsById(any())).thenReturn(false);
        doThrow(new RuntimeException("db down"))
                .when(paymentService).handlePaymentStatusUpdate(anyString(), anyString(), any());

        String msg = "{\"intentId\":\"01INTENT\",\"toStatus\":\"PAID\",\"amountPaid\":\"10.00\"}";
        assertThrows(MoneyEventProcessingException.class,
                () -> consumer.onPaymentStatusChanged(msg, ack));
        // The settlement event must stay uncommitted so the container retries / dead-letters it.
        verify(ack, never()).acknowledge();
    }

    @Test
    void dlqConsumer_persistsDeadLetterWithOriginalTopic() {
        MoneyDlqConsumer dlq = new MoneyDlqConsumer(failedRepo);
        RecordHeaders headers = new RecordHeaders();
        headers.add(MoneyDlqConsumer.ORIGINAL_TOPIC_HEADER,
                "mushex.payment.status.changed".getBytes(StandardCharsets.UTF_8));
        headers.add(MoneyDlqConsumer.EXCEPTION_MESSAGE_HEADER,
                "db down".getBytes(StandardCharsets.UTF_8));
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                MoneyEventErrorHandlingConfig.MONEY_DLQ_TOPIC, 0, 0L, null, "{\"intentId\":\"01INTENT\"}");
        headers.forEach(h -> record.headers().add(h));

        dlq.onDeadLetter(record, ack);

        ArgumentCaptor<FailedMoneyEventEntity> captor = ArgumentCaptor.forClass(FailedMoneyEventEntity.class);
        verify(failedRepo).save(captor.capture());
        FailedMoneyEventEntity saved = captor.getValue();
        assertEquals("mushex.payment.status.changed", saved.getOriginalTopic());
        assertEquals("{\"intentId\":\"01INTENT\"}", saved.getPayload());
        assertEquals("db down", saved.getErrorMessage());
        assertEquals(FailedMoneyEventEntity.STATUS_DEAD, saved.getStatus());
        verify(ack).acknowledge();
    }

    @Test
    void dlqConsumer_neverThrows_evenWhenPersistFails() {
        MoneyDlqConsumer dlq = new MoneyDlqConsumer(failedRepo);
        when(failedRepo.save(any())).thenThrow(new RuntimeException("db down"));
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                MoneyEventErrorHandlingConfig.MONEY_DLQ_TOPIC, 0, 0L, null, "{}");

        assertDoesNotThrow(() -> dlq.onDeadLetter(record, ack));
        verify(ack).acknowledge();
    }

    @Test
    void replay_republishesOriginalPayloadOntoOriginalTopic_andMarksReplayed() {
        TrustContextHolder.set(new TrustContext(
                UUID.randomUUID(), "ops-1", "SYSTEM_ADMIN", "FINANCE",
                "device-1", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        FailedMoneyEventEntity row = new FailedMoneyEventEntity();
        row.setId(7L);
        row.setOriginalTopic("mushex.payment.status.changed");
        row.setPayload("{\"intentId\":\"01INTENT\"}");
        when(failedRepo.findById(7L)).thenReturn(Optional.of(row));
        when(failedRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        FailedMoneyEventController controller = new FailedMoneyEventController(failedRepo, kafkaTemplate);

        FailedMoneyEventEntity replayed = controller.replay(7L).getBody();

        verify(kafkaTemplate).send(eq("mushex.payment.status.changed"), eq("{\"intentId\":\"01INTENT\"}"));
        assertNotNull(replayed);
        assertEquals(FailedMoneyEventEntity.STATUS_REPLAYED, replayed.getStatus());
        assertEquals("ops-1", replayed.getReplayedBy());
        assertNotNull(replayed.getReplayedAt());
    }

    @Test
    void replay_refusesDoubleReplay() {
        TrustContextHolder.set(new TrustContext(
                UUID.randomUUID(), "ops-1", "SYSTEM_ADMIN", "FINANCE",
                "device-1", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        FailedMoneyEventEntity row = new FailedMoneyEventEntity();
        row.setId(8L);
        row.setOriginalTopic("mushex.payment.status.changed");
        row.setPayload("{}");
        row.setStatus(FailedMoneyEventEntity.STATUS_REPLAYED);
        when(failedRepo.findById(8L)).thenReturn(Optional.of(row));
        FailedMoneyEventController controller = new FailedMoneyEventController(failedRepo, kafkaTemplate);

        assertThrows(IllegalStateException.class, () -> controller.replay(8L));
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }
}
