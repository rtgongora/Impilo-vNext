package zw.gov.mohcc.impilo.costa.kafka;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Bounded-retry + dead-letter error handling for COSTA's Kafka listeners.
 *
 * <p>Money consumers used to catch → log → ack, permanently dropping the event
 * on any transient failure: a lost {@code mushex.payment.status.changed} left a
 * bill unpaid forever with no recovery. Money consumers now rethrow via
 * {@link MoneyEventProcessingException}; this handler retries with backoff and
 * then publishes the original record to {@code costa.money.dlq}, where
 * {@link MoneyDlqConsumer} persists it for the ops replay surface.</p>
 *
 * <p>Consumers that deliberately swallow their own exceptions are unaffected —
 * the handler only engages when a listener throws.</p>
 */
@Configuration
public class MoneyEventErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(MoneyEventErrorHandlingConfig.class);

    public static final String MONEY_DLQ_TOPIC = "costa.money.dlq";

    /** 3 redeliveries, 1s apart, before the record is dead-lettered. */
    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    log.error("Dead-lettering money event from topic {} after retries: {}",
                            record.topic(), ex.getMessage());
                    // Single DLQ partition-agnostic routing; original topic travels in
                    // the kafka_dlt-original-topic header the recoverer adds.
                    return new TopicPartition(MONEY_DLQ_TOPIC, -1);
                });
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    }
}
