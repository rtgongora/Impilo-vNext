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
}
