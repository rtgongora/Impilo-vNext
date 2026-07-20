package zw.gov.mohcc.impilo.ubomi.events;

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
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * UBOMI outbox publisher (W1a) — closes a real gap: since V001 the birth/death/
 * CRVS flows have written rows to {@code ubomi.event_outbox} via
 * {@code publishEvent(...)}, but there was <b>no publisher</b>, so nothing was
 * ever emitted. This mirrors {@code ButanoOutboxPublisher} on the shared
 * {@link CompanionOutboxPublisher} base: a {@code @Scheduled} drain of
 * unpublished rows ({@code published_at IS NULL}) with dual emit (legacy topics
 * + v1.1 {@code EventEnvelope}) over {@code KafkaTemplate<String,String>} with
 * StringSerializer (the outbox house style — payloads are pre-serialized JSON
 * strings; a JsonSerializer would double-encode).
 *
 * <p>Emits the pre-existing BIRTH_* / DEATH_* / CRVS_PACKAGE_READY events plus
 * the new {@code civil.*} governed events produced by the RG adapter (W1c).</p>
 *
 * <p>Not RG-gated: this runs whether or not {@code ubomi.rg.enabled} is set.</p>
 */
@Component
public class UbomiOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(UbomiOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public UbomiOutboxPublisher(EventOutboxRepository outboxRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${ubomi.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("ubomi"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("UbomiOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${ubomi.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int count = publishPendingEvents();
        if (count > 0) {
            log.info("Published {} ubomi outbox events", count);
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
            entity.setPublishedAt(publishedAt);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected void markFailed(OutboxRow row, String errorMessage) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishError(errorMessage);
            entity.setRetryCount(row.retryCount() + 1);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        if (row.eventType() == null) {
            return "ubomi.events";
        }
        return switch (row.eventType()) {
            case "BIRTH_SUBMITTED"          -> "ubomi.birth.submitted";
            case "BIRTH_REGISTERED"         -> "ubomi.birth.registered";
            case "DEATH_SUBMITTED"          -> "ubomi.death.submitted";
            case "DEATH_CERTIFIED"          -> "ubomi.death.certified";
            case "CRVS_PACKAGE_READY"       -> "ubomi.crvs.package_ready";
            case "DEATH_REGISTERED"         -> "ubomi.death.registered";
            // Governed civil.* events (W1c) — literal topic names per design doc.
            case "CIVIL_IDENTITY_VERIFIED"  -> "civil.identity.verified";
            case "CIVIL_BIRTH_CONFIRMED"    -> "civil.birth.confirmed";
            case "CIVIL_DEATH_CONFIRMED"    -> "civil.death.confirmed";
            case "CIVIL_IDENTITY_CORRECTED" -> "civil.identity.corrected";
            case "CIVIL_IDENTITY_DISPUTED"  -> "civil.identity.disputed";
            default                         -> "ubomi.events";
        };
    }
}
