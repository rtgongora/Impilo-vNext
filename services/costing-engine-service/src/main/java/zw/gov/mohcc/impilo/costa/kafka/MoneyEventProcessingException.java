package zw.gov.mohcc.impilo.costa.kafka;

/**
 * Thrown by money-critical Kafka listeners instead of swallowing the failure,
 * so the container error handler retries the record and dead-letters it to
 * {@code costa.money.dlq} rather than acking a dropped settlement/refund/charge.
 */
public class MoneyEventProcessingException extends RuntimeException {

    public MoneyEventProcessingException(String topic, Throwable cause) {
        super("Money event processing failed for topic " + topic, cause);
    }
}
