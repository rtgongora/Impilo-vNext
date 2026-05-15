package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * VITO Event Outbox Publisher — refactored to use shared CompanionOutboxPublisher.
 *
 * <h3>Emit mode precedence</h3>
 * <ol>
 *   <li>{@code EMIT_MODE} system property (highest priority)</li>
 *   <li>{@code EMIT_MODE} environment variable</li>
 *   <li>{@code vito.v11.emit-mode} from application.yml</li>
 *   <li>Default: DUAL</li>
 * </ol>
 */
@Component
public class VitoOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(VitoOutboxPublisher.class);
    private static final String V11_IDENTITY_TOPIC = "impilo.vito.identity";

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean dualEmitKernelClientRegisteredEnabled;
    private final String kernelClientRegisteredTopic;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VitoOutboxPublisher(EventOutboxRepository outboxRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${vito.v11.emit-mode:#{null}}") String ymlEmitMode,
                                @Value("${vito.outbox.dual-emit-kernel-client-registered-enabled:false}") boolean dualEmitKernelClientRegisteredEnabled,
                                @Value("${vito.outbox.kernel-client-registered-topic:kernel.vito.client.registered}") String kernelClientRegisteredTopic) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("vito"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.dualEmitKernelClientRegisteredEnabled = dualEmitKernelClientRegisteredEnabled;
        this.kernelClientRegisteredTopic = kernelClientRegisteredTopic;
        log.info("VitoOutboxPublisher initialized with effective emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${vito.outbox.poll-interval-ms:2000}")
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
        if (dualEmitKernelClientRegisteredEnabled
                && V11_IDENTITY_TOPIC.equals(topic)
                && shouldEmitKernelClientRegisteredAlias(value)
                && !kernelClientRegisteredTopic.equals(topic)) {
            kafkaTemplate.send(kernelClientRegisteredTopic, key, value);
        }
    }

    @Override
    protected void markPublished(OutboxRow row, OffsetDateTime publishedAt) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishedAt(publishedAt);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected void markFailed(OutboxRow row, String errorMessage) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishError(errorMessage);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return VitoEventMapper.resolveLegacyTopic(row.aggregateType());
    }

    @Override
    protected String resolveV11Topic(OutboxRow row) {
        return VitoEventMapper.resolveV11Topic(row.aggregateType());
    }

    private boolean shouldEmitKernelClientRegisteredAlias(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("event_type").asText(null);
            if (eventType == null || eventType.isBlank()) {
                eventType = root.path("eventType").asText(null);
            }
            if (eventType == null || eventType.isBlank()) {
                return false;
            }
            return eventType.contains(".client.created.")
                    || eventType.contains(".client.registered.")
                    || eventType.contains(".client.completed.");
        } catch (Exception ex) {
            log.debug("Skipping kernel.vito.client.registered alias emit due to unparseable payload", ex);
            return false;
        }
    }
}
