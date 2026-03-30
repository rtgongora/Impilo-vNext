package zw.gov.mohcc.impilo.inpatient.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DualEmitPolicy emitPolicy;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${inpatient.v11.emit-mode:#{null}}") String ymlEmitMode) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.emitPolicy = new DualEmitPolicy(ymlEmitMode);
    }

    @Scheduled(fixedDelayString = "${inpatient.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending =
                outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) return;

        int published = 0;
        for (EventOutboxEntity event : pending) {
            try {
                String key = event.getId();
                String payload = event.getPayloadJson();

                if (emitPolicy.emitV11()) {
                    kafkaTemplate.send(routeTopic(event.getEventType()), key, payload);
                }
                if (emitPolicy.emitLegacy()) {
                    kafkaTemplate.send(routeLegacyTopic(event.getEventType()), key, payload);
                }

                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                published++;
            } catch (Exception e) {
                log.error("Failed to publish inpatient outbox event id={} type={}",
                        event.getId(), event.getEventType(), e);
            }
        }
        if (published > 0) {
            log.info("Published {}/{} inpatient outbox events", published, pending.size());
        }
    }

    static String routeTopic(String eventType) {
        if (eventType == null) return "clinical.inpatient.events";
        return switch (eventType) {
            case "ADMISSION_CREATED" -> "clinical.inpatient.admission.created";
            case "PATIENT_TRANSFERRED" -> "clinical.inpatient.admission.transferred";
            case "PATIENT_DISCHARGED" -> "clinical.inpatient.admission.discharged";
            case "DEATH_DISCHARGE" -> "clinical.inpatient.admission.death_discharge";
            case "BED_ASSIGNED" -> "clinical.inpatient.bed.assigned";
            case "BED_RELEASED" -> "clinical.inpatient.bed.released";
            case "WARD_UPDATED" -> "clinical.inpatient.ward.updated";
            default -> "clinical.inpatient.events";
        };
    }

    static String routeLegacyTopic(String eventType) {
        if (eventType == null) return "inpatient.events";
        return switch (eventType) {
            case "ADMISSION_CREATED" -> "inpatient.admission.created";
            case "PATIENT_TRANSFERRED" -> "inpatient.admission.transferred";
            case "PATIENT_DISCHARGED" -> "inpatient.admission.discharged";
            case "DEATH_DISCHARGE" -> "inpatient.admission.death_discharge";
            case "BED_ASSIGNED" -> "inpatient.bed.assigned";
            case "BED_RELEASED" -> "inpatient.bed.released";
            case "WARD_UPDATED" -> "inpatient.ward.updated";
            default -> "inpatient.events";
        };
    }
}
