package zw.gov.mohcc.impilo.procurement.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procurement.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.procurement.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Profile("!test")
public class ProcOutboxPublisher extends CompanionOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(ProcOutboxPublisher.class);
    private final EventOutboxRepository repo;
    private final KafkaTemplate<String, String> kafka;

    public ProcOutboxPublisher(EventOutboxRepository repo, KafkaTemplate<String, String> kafka,
                               @Value("${procurement.v11.emit-mode:#{null}}") String mode) {
        super(new DualEmitPolicy(mode), new EventTopicRegistry("procurement"));
        this.repo = repo;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${procurement.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int n = publishPendingEvents();
        if (n > 0) {
            log.info("Published {} procurement outbox events", n);
        }
    }

    @Override
    protected List<? extends OutboxRow> fetchUnpublished() {
        return repo.findUnpublished(PageRequest.of(0, 100)).stream().map(EventOutboxEntity::toOutboxRow).toList();
    }

    @Override
    protected void sendToKafka(String topic, String key, String value) {
        kafka.send(topic, key, value);
    }

    @Override
    protected void markPublished(OutboxRow row, OffsetDateTime publishedAt) {
        repo.findById(row.id()).ifPresent(e -> {
            e.setPublishedAt(publishedAt);
            repo.save(e);
        });
    }

    @Override
    protected void markFailed(OutboxRow row, String err) {
        repo.findById(row.id()).ifPresent(e -> {
            e.setPublishError(err);
            e.setRetryCount(e.getRetryCount() + 1);
            repo.save(e);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return switch (row.aggregateType()) {
            case "PURCHASE_ORDER" -> "procurement.po.created";
            case "GOODS_RECEIVED" -> "procurement.grn.received";
            case "SUPPLIER_INVOICE" -> "procurement.invoice.matched";
            case "SUPPLIER_PAYMENT" -> "procurement.payment.completed";
            default -> "procurement.events";
        };
    }
}
