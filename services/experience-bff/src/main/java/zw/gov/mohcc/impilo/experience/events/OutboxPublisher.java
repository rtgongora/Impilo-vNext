package zw.gov.mohcc.impilo.experience.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Previously polled the event_outbox table and published unpublished events to Kafka.
 * The BFF database has been removed; outbox publishing is now handled by sovereign services.
 * This component is retained as a no-op stub for graceful transition.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    public void publishPendingEvents() {
        // No-op: BFF database removed; outbox publishing delegated to sovereign services
    }
}
