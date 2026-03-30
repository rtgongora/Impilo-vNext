package zw.gov.mohcc.impilo.pct.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final int BATCH_SIZE = 100;

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DualEmitPolicy emitPolicy;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${pct.v11.emit-mode:#{null}}") String ymlEmitMode) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.emitPolicy = new DualEmitPolicy(ymlEmitMode);
    }

    @Scheduled(fixedDelayString = "${pct.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        if (pending.isEmpty()) {
            return;
        }

        List<EventOutboxEntity> batch = pending.size() > BATCH_SIZE
                ? pending.subList(0, BATCH_SIZE)
                : pending;

        int successCount = 0;
        for (EventOutboxEntity event : batch) {
            try {
                String key = event.getAggregateId();
                String payload = event.getPayload();

                if (emitPolicy.emitV11()) {
                    kafkaTemplate.send(routeTopic(event.getEventType()), key, payload);
                }
                if (emitPolicy.emitLegacy()) {
                    kafkaTemplate.send(routeLegacyTopic(event.getEventType()), key, payload);
                }

                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                successCount++;

            } catch (Exception e) {
                log.error("Failed to publish outbox event {} (type={}) to Kafka: {}",
                        event.getId(), event.getEventType(), e.getMessage(), e);
            }
        }

        if (successCount > 0) {
            log.info("Published {}/{} outbox events to Kafka", successCount, batch.size());
        }
    }

    static String routeTopic(String eventType) {
        if (eventType == null) {
            return "clinical.pct.events";
        }

        return switch (eventType) {
            case "JOURNEY_CREATED", "JOURNEY_STATE_CHANGED" -> "clinical.pct.journey.state_changed";

            case "QUEUE_ITEM_CREATED", "QUEUE_ITEM_UPDATED",
                 "QUEUE_ITEM_ENQUEUED", "QUEUE_ITEM_CALLED",
                 "QUEUE_ITEM_TRANSFERRED" -> "clinical.pct.queue.item_changed";

            case "ENCOUNTER_STARTED" -> "clinical.pct.encounter.started";
            case "ENCOUNTER_COMPLETED" -> "clinical.pct.encounter.completed";

            case "ADMISSION_CREATED", "ADMISSION_UPDATED",
                 "ADMISSION_REQUESTED", "PATIENT_ADMITTED" -> "clinical.pct.admission.updated";

            case "DISCHARGE_INITIATED", "DISCHARGE_STARTED" -> "clinical.pct.discharge.started";
            case "DISCHARGE_COMPLETED" -> "clinical.pct.discharge.completed";

            case "DEATH_RECORDED" -> "clinical.pct.death.recorded";
            case "DEATH_CASE_COMPLETED", "DEATH_COMPLETED" -> "clinical.pct.death.completed";

            case "TASK_CREATED", "TASK_COMPLETED" -> "clinical.pct.task.updated";

            case "TRIAGE_RECORDED" -> "clinical.pct.triage.recorded";
            case "TRANSFER_REQUESTED", "TRANSFER_COMPLETED" -> "clinical.pct.transfer.updated";

            default -> "clinical.pct.events";
        };
    }

    static String routeLegacyTopic(String eventType) {
        if (eventType == null) {
            return "pct.events";
        }

        return switch (eventType) {
            case "JOURNEY_CREATED", "JOURNEY_STATE_CHANGED" -> "pct.journey.state_changed";

            case "QUEUE_ITEM_CREATED", "QUEUE_ITEM_UPDATED",
                 "QUEUE_ITEM_ENQUEUED", "QUEUE_ITEM_CALLED",
                 "QUEUE_ITEM_TRANSFERRED" -> "pct.queue.item.updated";

            case "ENCOUNTER_STARTED" -> "pct.encounter.started";
            case "ENCOUNTER_COMPLETED" -> "pct.encounter.completed";

            case "ADMISSION_CREATED", "ADMISSION_UPDATED",
                 "ADMISSION_REQUESTED", "PATIENT_ADMITTED" -> "pct.admission.updated";

            case "DISCHARGE_INITIATED", "DISCHARGE_STARTED" -> "pct.discharge.started";
            case "DISCHARGE_COMPLETED" -> "pct.discharge.completed";

            case "DEATH_RECORDED" -> "pct.death.recorded";
            case "DEATH_CASE_COMPLETED", "DEATH_COMPLETED" -> "pct.death.completed";

            case "TASK_CREATED", "TASK_COMPLETED" -> "pct.task.updated";

            case "TRIAGE_RECORDED" -> "pct.triage.recorded";
            case "TRANSFER_REQUESTED", "TRANSFER_COMPLETED" -> "pct.transfer.updated";

            default -> "pct.events";
        };
    }
}
