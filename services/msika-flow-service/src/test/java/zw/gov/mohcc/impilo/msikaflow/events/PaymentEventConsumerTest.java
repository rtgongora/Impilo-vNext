package zw.gov.mohcc.impilo.msikaflow.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.msikaflow.core.CommitmentService;
import zw.gov.mohcc.impilo.msikaflow.core.PaymentService;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * OF-B10 — payment events route to BOTH consumers: the legacy order payment
 * path AND the marketplace commitment resume seam; malformed events are
 * swallowed (logged), never thrown back at the Kafka listener.
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock private PaymentService paymentService;
    @Mock private CommitmentService commitmentService;

    private PaymentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentEventConsumer(paymentService, commitmentService, new ObjectMapper());
        lenient().when(commitmentService.onPaymentStatusChanged(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void paidEvent_routesToOrderPath_andCommitmentResume() {
        consumer.onPaymentStatusChanged("{\"intentId\":\"mpi-9\",\"status\":\"PAID\"}");

        verify(paymentService).handlePaymentCallback("mpi-9", "PAID", "SYSTEM");
        verify(commitmentService).onPaymentStatusChanged("mpi-9", "PAID");
    }

    @Test
    void envelopeEvent_unwrapsPayload() {
        consumer.onPaymentStatusChanged(
                "{\"payload\":{\"paymentIntentId\":\"mpi-10\",\"toStatus\":\"FAILED\",\"actorId\":\"cashier-1\"}}");

        verify(paymentService).handlePaymentCallback("mpi-10", "FAILED", "cashier-1");
        verify(commitmentService).onPaymentStatusChanged("mpi-10", "FAILED");
    }

    @Test
    void missingIntentId_isIgnoredEntirely() {
        consumer.onPaymentStatusChanged("{\"status\":\"PAID\"}");

        verifyNoInteractions(paymentService);
        verifyNoInteractions(commitmentService);
    }

    @Test
    void malformedJson_neverThrows() {
        consumer.onPaymentStatusChanged("not-json");

        verifyNoInteractions(commitmentService);
    }

    @Test
    void commitmentResumeFailure_doesNotPoisonTheListener() {
        when(commitmentService.onPaymentStatusChanged(anyString(), anyString()))
                .thenThrow(new IllegalStateException("boom"));

        // must not propagate — the listener catches and logs
        consumer.onPaymentStatusChanged("{\"intentId\":\"mpi-11\",\"status\":\"PAID\"}");
        verify(paymentService).handlePaymentCallback("mpi-11", "PAID", "SYSTEM");
    }

    // ── M3 BUG-7 — path isolation, both orders ──────────────────────────

    @Test
    void legacySettlementNotFound_neverSwallowsTheMarketplaceResume() {
        // Marketplace selection intents have NO legacy mf_settlements row — the
        // legacy path throws; the marketplace resume must still run (this was
        // the live-proof blocker: every paid selection stranded AWAITING_PAYMENT).
        doThrow(new IllegalArgumentException("Settlement not found for MUSHEX ID: mpi-12"))
                .when(paymentService).handlePaymentCallback("mpi-12", "PAID", "SYSTEM");

        consumer.onPaymentStatusChanged("{\"intentId\":\"mpi-12\",\"status\":\"PAID\"}");

        verify(commitmentService).onPaymentStatusChanged("mpi-12", "PAID");
    }

    @Test
    void marketplaceResumeRunsFirst_andLegacyStillRunsAfterIt() {
        consumer.onPaymentStatusChanged("{\"intentId\":\"mpi-13\",\"status\":\"PAID\"}");

        var order = inOrder(commitmentService, paymentService);
        order.verify(commitmentService).onPaymentStatusChanged("mpi-13", "PAID");
        order.verify(paymentService).handlePaymentCallback("mpi-13", "PAID", "SYSTEM");
    }

    @Test
    void marketplaceResumeFailure_neverSwallowsTheLegacyPath() {
        when(commitmentService.onPaymentStatusChanged("mpi-14", "PAID"))
                .thenThrow(new IllegalStateException("marketplace boom"));

        consumer.onPaymentStatusChanged("{\"intentId\":\"mpi-14\",\"status\":\"PAID\"}");

        verify(paymentService).handlePaymentCallback("mpi-14", "PAID", "SYSTEM");
    }

    @Test
    void legacyUnexpectedFailure_isIsolatedToo() {
        doThrow(new RuntimeException("legacy db down"))
                .when(paymentService).handlePaymentCallback("mpi-15", "FAILED", "SYSTEM");

        // never propagates to the Kafka listener
        consumer.onPaymentStatusChanged("{\"intentId\":\"mpi-15\",\"status\":\"FAILED\"}");
        verify(commitmentService).onPaymentStatusChanged("mpi-15", "FAILED");
    }
}
