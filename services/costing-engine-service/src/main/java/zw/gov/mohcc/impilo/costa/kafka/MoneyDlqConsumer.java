package zw.gov.mohcc.impilo.costa.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.costa.domain.entity.FailedMoneyEventEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.FailedMoneyEventRepository;

import java.nio.charset.StandardCharsets;

/**
 * Persists dead-lettered money events into {@code costa_failed_money_events}
 * so operations has a durable, queryable record of every dropped
 * settlement/refund/charge event — a Kafka topic alone is not an ops surface.
 *
 * <p>This consumer must never throw: a failure to persist a dead letter is
 * logged loudly but acked, because redelivering the DLQ record cannot make the
 * original event less lost.</p>
 */
@Component
public class MoneyDlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(MoneyDlqConsumer.class);

    /** Header the DeadLetterPublishingRecoverer stamps with the source topic. */
    static final String ORIGINAL_TOPIC_HEADER = "kafka_dlt-original-topic";
    static final String EXCEPTION_MESSAGE_HEADER = "kafka_dlt-exception-message";

    private final FailedMoneyEventRepository repository;

    public MoneyDlqConsumer(FailedMoneyEventRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = MoneyEventErrorHandlingConfig.MONEY_DLQ_TOPIC, groupId = "costa-costing-engine")
    public void onDeadLetter(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            FailedMoneyEventEntity row = new FailedMoneyEventEntity();
            row.setOriginalTopic(header(record, ORIGINAL_TOPIC_HEADER, "unknown"));
            row.setPayload(record.value() != null ? record.value() : "");
            row.setErrorMessage(header(record, EXCEPTION_MESSAGE_HEADER, null));
            repository.save(row);
            log.error("MONEY EVENT DEAD-LETTERED: originalTopic={} storedId={} — needs ops replay",
                    row.getOriginalTopic(), row.getId());
        } catch (Exception e) {
            log.error("Failed to persist dead-lettered money event (payload retained on {}): {}",
                    MoneyEventErrorHandlingConfig.MONEY_DLQ_TOPIC, record.value(), e);
        }
        ack.acknowledge();
    }

    private static String header(ConsumerRecord<String, String> record, String name, String fallback) {
        Header h = record.headers().lastHeader(name);
        return h != null ? new String(h.value(), StandardCharsets.UTF_8) : fallback;
    }
}
