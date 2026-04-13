package zw.gov.mohcc.impilo.hrpayroll.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.hrpayroll.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Profile("!test")
public class HrOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(HrOutboxPublisher.class);
    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public HrOutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${hr.v11.emit-mode:#{null}}") String emitMode) {
        super(new DualEmitPolicy(emitMode), new EventTopicRegistry("hrpayroll"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${hr.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int n = publishPendingEvents();
        if (n > 0) {
            log.info("Published {} HR payroll outbox events", n);
        }
    }

    @Override
    protected List<? extends OutboxRow> fetchUnpublished() {
        return outboxRepository.findUnpublished(PageRequest.of(0, 100)).stream()
                .map(EventOutboxEntity::toOutboxRow)
                .toList();
    }

    @Override
    protected void sendToKafka(String topic, String key, String value) {
        kafkaTemplate.send(topic, key, value);
    }

    @Override
    protected void markPublished(OutboxRow row, OffsetDateTime publishedAt) {
        outboxRepository.findById(row.id()).ifPresent(e -> {
            e.setPublishedAt(publishedAt);
            outboxRepository.save(e);
        });
    }

    @Override
    protected void markFailed(OutboxRow row, String errorMessage) {
        outboxRepository.findById(row.id()).ifPresent(e -> {
            e.setPublishError(errorMessage);
            e.setRetryCount(e.getRetryCount() + 1);
            outboxRepository.save(e);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return switch (row.aggregateType()) {
            case "PAYROLL_RUN" -> "payroll.run.completed";
            case "PAYSLIP" -> "payroll.payslip.generated";
            default -> "payroll.events";
        };
    }
}
