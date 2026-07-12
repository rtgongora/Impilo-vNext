package zw.gov.mohcc.impilo.mushex.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.verify;

/**
 * The Msika Flow Kafka listeners are retired to pure log-only observers (intent
 * creation and refund execution are Msika-Flow-driven over HTTP). This test locks
 * that: the consumer has no PaymentIntentService/RefundService dependency and simply
 * acknowledges — it can never create an intent or trigger a refund from Kafka again.
 */
@ExtendWith(MockitoExtension.class)
class MsikaFlowEventConsumerRetirementTest {

    @Mock Acknowledgment ack;

    MsikaFlowEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MsikaFlowEventConsumer(new ObjectMapper());
    }

    @Test
    void onOrderPaid_isLogOnly_acknowledges() {
        consumer.onOrderPaid("{\"orderId\":\"ORDER1\",\"tenantId\":\"t1\",\"totalAmount\":\"25.00\"}", ack);
        verify(ack).acknowledge();
    }

    @Test
    void onOrderPaid_malformed_stillAcknowledges() {
        consumer.onOrderPaid("not json", ack);
        verify(ack).acknowledge();
    }

    @Test
    void onRefundRequested_isLogOnly_acknowledges() {
        consumer.onRefundRequested("{\"orderId\":\"ORDER1\",\"refundAmount\":\"10.00\"}", ack);
        verify(ack).acknowledge();
    }
}
