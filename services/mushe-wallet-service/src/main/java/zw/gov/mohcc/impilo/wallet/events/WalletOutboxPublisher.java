package zw.gov.mohcc.impilo.wallet.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;
import zw.gov.mohcc.impilo.wallet.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Mushe Wallet outbox publisher using shared CompanionOutboxPublisher base.
 *
 * <p>Polls the {@code mushe.event_outbox} table and publishes unpublished events
 * to Kafka using the transactional outbox pattern. Supports both legacy topic
 * routing and v1.1 EventEnvelope format via dual-emit.</p>
 *
 * <p>Legacy topic routing:
 * <ul>
 *   <li>WALLET -> mushe.wallet</li>
 *   <li>MERCHANT -> mushe.merchant</li>
 *   <li>TRANSACTION -> mushe.transactions</li>
 *   <li>CARD -> mushe.cards</li>
 *   <li>HOLD -> mushe.holds</li>
 *   <li>default -> mushe.events</li>
 * </ul>
 */
@Component
public class WalletOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(WalletOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public WalletOutboxPublisher(EventOutboxRepository outboxRepository,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  @Value("${mushe.wallet.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("mushe-wallet"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("WalletOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${mushe.wallet.outbox.poll-interval-ms:1000}")
    @Transactional
    public void poll() {
        int count = publishPendingEvents();
        if (count > 0) {
            log.info("Published {} outbox events", count);
        }
    }

    @Override
    protected List<OutboxRow> fetchUnpublished() {
        return outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
                .stream()
                .map(EventOutboxEntity::toOutboxRow)
                .toList();
    }

    @Override
    protected void sendToKafka(String topic, String key, String value) {
        kafkaTemplate.send(topic, key, value);
    }

    @Override
    protected void markPublished(OutboxRow row, OffsetDateTime publishedAt) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishedAt(OffsetDateTime.now());
            outboxRepository.save(entity);
        });
    }

    @Override
    protected void markFailed(OutboxRow row, String errorMessage) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishError(errorMessage);
            entity.setRetryCount(entity.getRetryCount() != null ? entity.getRetryCount() + 1 : 1);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return switch (row.aggregateType()) {
            case "WALLET" -> "mushe.wallet";
            case "MERCHANT" -> "mushe.merchant";
            case "TRANSACTION" -> "mushe.transactions";
            case "CARD" -> "mushe.cards";
            case "HOLD" -> "mushe.holds";
            default -> "mushe.events";
        };
    }
}
