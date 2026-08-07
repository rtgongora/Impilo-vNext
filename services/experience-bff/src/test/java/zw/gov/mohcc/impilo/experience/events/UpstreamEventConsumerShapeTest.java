package zw.gov.mohcc.impilo.experience.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every listener in {@link UpstreamEventConsumer} subscribes to a legacy topic and its v1.1
 * counterpart at once, so one method has to read two wire shapes.
 *
 * <p>That was harmless only while the v1.1 topics had no producer. As services convert to
 * CompanionOutboxPublisher they gain one, and a root-level {@code path("orderId")} against an
 * envelope resolves to an empty string rather than failing — the handler logs a blank field
 * and nothing anywhere goes red. These tests pin both shapes so that regression cannot
 * return silently.</p>
 */
@DisplayName("UpstreamEventConsumer reads both wire shapes")
class UpstreamEventConsumerShapeTest {

    private static final String LEGACY_ORDER =
            "{\"orderId\":\"ORD-1\",\"status\":\"COMPLETED\"}";

    /** What oros now publishes to impilo.oros.order. */
    private static final String ENVELOPE_ORDER = """
            {"event_id":"e-1","event_type":"impilo.oros.order.status_changed.v1",\
            "schema_version":1,"correlation_id":"c-1","causation_id":"c-2",\
            "idempotency_key":"i-1","producer":"oros","tenant_id":"t-1","pod_id":"national",\
            "occurred_at":"2026-08-07T09:00:00Z","emitted_at":"2026-08-07T09:00:01Z",\
            "subject_type":"ORDER","subject_id":"ORD-1",\
            "payload":{"orderId":"ORD-1","status":"COMPLETED"},"meta":{}}""";

    private ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(UpstreamEventConsumer.class);
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private String messageFrom(ListAppender<ILoggingEvent> appender) {
        assertThat(appender.list).isNotEmpty();
        return appender.list.get(0).getFormattedMessage();
    }

    @Test
    @DisplayName("legacy raw payload still resolves the domain fields")
    void legacyShapeResolves() {
        UpstreamEventConsumer consumer = new UpstreamEventConsumer(new ObjectMapper());
        ListAppender<ILoggingEvent> appender = attachAppender();

        consumer.onOrderStatusChanged(LEGACY_ORDER);

        assertThat(messageFrom(appender)).contains("orderId=ORD-1").contains("status=COMPLETED");
    }

    @Test
    @DisplayName("a v1.1 envelope resolves the same fields from under payload")
    void envelopeShapeResolves() {
        UpstreamEventConsumer consumer = new UpstreamEventConsumer(new ObjectMapper());
        ListAppender<ILoggingEvent> appender = attachAppender();

        consumer.onOrderStatusChanged(ENVELOPE_ORDER);

        assertThat(messageFrom(appender))
                .as("reading the envelope root yields empty strings, not an error")
                .contains("orderId=ORD-1")
                .contains("status=COMPLETED");
    }

    @Test
    @DisplayName("the pharmacy dispense listener reads both shapes too")
    void dispenseReadsBothShapes() {
        UpstreamEventConsumer consumer = new UpstreamEventConsumer(new ObjectMapper());

        ListAppender<ILoggingEvent> legacy = attachAppender();
        consumer.onDispenseCompleted("{\"prescriptionId\":\"RX-1\"}");
        assertThat(messageFrom(legacy)).contains("prescriptionId=RX-1");

        ListAppender<ILoggingEvent> enveloped = attachAppender();
        consumer.onDispenseCompleted(
                "{\"event_id\":\"e-2\",\"payload\":{\"prescriptionId\":\"RX-1\"},\"meta\":{}}");
        assertThat(messageFrom(enveloped)).contains("prescriptionId=RX-1");
    }

    @Test
    @DisplayName("a payload that is not an object falls back to the root rather than throwing")
    void nonObjectPayloadFallsBackToRoot() {
        UpstreamEventConsumer consumer = new UpstreamEventConsumer(new ObjectMapper());
        ListAppender<ILoggingEvent> appender = attachAppender();

        // A legacy producer whose domain payload happens to have a scalar "payload" field.
        consumer.onOrderStatusChanged("{\"orderId\":\"ORD-9\",\"status\":\"NEW\",\"payload\":\"n/a\"}");

        assertThat(messageFrom(appender)).contains("orderId=ORD-9");
    }
}
