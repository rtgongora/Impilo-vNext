package zw.gov.mohcc.impilo.mushex.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import zw.gov.mohcc.impilo.mushex.domain.entity.IdempotencyEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.IdempotencyRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.RefundService;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Double-bill quarantine guard for {@code costa.bill.finalized}.
 *
 * <p>Payment intents for COSTA bills are created exclusively through COSTA's
 * synchronous handoff ({@code costa PaymentIntegrationService} →
 * {@code MushexPaymentIntentClient}), keyed {@code COSTA_PAYMENT:{paymentId}}.
 * The Kafka consumer {@link CostaEventConsumer#onBillFinalized} MUST remain a
 * pure observer: if it ever creates intents again it would (a) duplicate the
 * synchronous intent, (b) charge the FULL bill amount instead of the patient
 * shortfall, and (c) blow up on the trust context on the listener thread.
 * See board serialized item R3 and
 * {@code docs/finance/costa-mushex-coverage-capability-map.md}.</p>
 *
 * <p>If a deliberate, coordinator-approved activation of this consumer ever
 * lands, these tests are the tripwire: they must be replaced by tests proving
 * bill-ID-scoped intent uniqueness and replay safety.</p>
 */
@ExtendWith(MockitoExtension.class)
class CostaEventConsumerBillFinalizedQuarantineTest {

    @Mock private PaymentIntentService intentService;
    @Mock private RefundService refundService;
    @Mock private PaymentIntentRepository intentRepository;
    @Mock private IdempotencyRepository idempotencyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CostaEventConsumer consumer() {
        return new CostaEventConsumer(
                intentService, refundService, intentRepository, idempotencyRepository, objectMapper);
    }

    /** In-memory idempotency store so replay semantics are exercised for real. */
    private Set<String> wireInMemoryIdempotency() {
        Set<String> processed = new HashSet<>();
        when(idempotencyRepository.existsById(any(String.class)))
                .thenAnswer(inv -> processed.contains(inv.getArgument(0, String.class)));
        when(idempotencyRepository.save(any(IdempotencyEntity.class))).thenAnswer(inv -> {
            IdempotencyEntity e = inv.getArgument(0, IdempotencyEntity.class);
            processed.add(e.getIdempotencyKey());
            return e;
        });
        return processed;
    }

    private static final String BILL_FINALIZED_EVENT = """
            {
              "eventId": "evt-bill-final-001",
              "billId": "01JBILLFINALIZED0000000001",
              "encounterId": "enc-77",
              "totalPayable": "120.00",
              "insurerPayable": "80.00",
              "patientPayable": "40.00",
              "currency": "USD"
            }
            """;

    @Test
    void onBillFinalized_neverCreatesPaymentIntents() {
        wireInMemoryIdempotency();
        AtomicInteger acks = new AtomicInteger();
        Acknowledgment ack = acks::incrementAndGet;

        consumer().onBillFinalized(BILL_FINALIZED_EVENT, ack);

        // Observer only: no intent creation, no intent lookups, no writes.
        verifyNoInteractions(intentService);
        verifyNoInteractions(intentRepository);
        assertEquals(1, acks.get(), "event must be acknowledged");
    }

    @Test
    void onBillFinalized_replayedEvent_isDedupedAndStillCreatesNothing() {
        Set<String> processed = wireInMemoryIdempotency();
        AtomicInteger acks = new AtomicInteger();
        Acknowledgment ack = acks::incrementAndGet;

        CostaEventConsumer consumer = consumer();
        consumer.onBillFinalized(BILL_FINALIZED_EVENT, ack);
        consumer.onBillFinalized(BILL_FINALIZED_EVENT, ack);
        consumer.onBillFinalized(BILL_FINALIZED_EVENT, ack);

        verifyNoInteractions(intentService);
        verifyNoInteractions(intentRepository);
        assertEquals(3, acks.get(), "every delivery must be acknowledged");
        assertEquals(Set.of("COSTA:evt-bill-final-001"), processed,
                "replays must collapse onto a single idempotency record");
    }

    @Test
    void onBillFinalized_malformedPayload_acksWithoutFinancialSideEffects() {
        AtomicInteger acks = new AtomicInteger();
        Acknowledgment ack = acks::incrementAndGet;

        consumer().onBillFinalized("this is not json", ack);

        verifyNoInteractions(intentService);
        verifyNoInteractions(intentRepository);
        assertEquals(1, acks.get());
    }
}
