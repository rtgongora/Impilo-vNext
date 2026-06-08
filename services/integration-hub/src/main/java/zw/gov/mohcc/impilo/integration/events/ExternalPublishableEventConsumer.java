package zw.gov.mohcc.impilo.integration.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.integration.governance.service.ExternalPublishableWebhookDispatcher;

/**
 * Consumes externally publishable platform events and projects them to registered webhook subscriptions.
 */
@Component
public class ExternalPublishableEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExternalPublishableEventConsumer.class);

    private final ExternalPublishableWebhookDispatcher dispatcher;

    public ExternalPublishableEventConsumer(ExternalPublishableWebhookDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            topics = {
                    ExternalPublishableEventTopics.PATIENT_REGISTERED,
                    ExternalPublishableEventTopics.PATIENT_UPDATED,
                    ExternalPublishableEventTopics.ENCOUNTER_STARTED,
                    ExternalPublishableEventTopics.ENCOUNTER_CLOSED,
                    ExternalPublishableEventTopics.LAB_ORDER_CREATED,
                    ExternalPublishableEventTopics.LAB_RESULT_RECEIVED,
                    ExternalPublishableEventTopics.IMAGING_ORDER_CREATED,
                    ExternalPublishableEventTopics.IMAGING_REPORT_RECEIVED,
                    ExternalPublishableEventTopics.STOCK_LEVEL_UPDATED,
                    ExternalPublishableEventTopics.STOCK_REQUISITION_CREATED,
                    ExternalPublishableEventTopics.PAYMENT_INITIATED,
                    ExternalPublishableEventTopics.PAYMENT_COMPLETED,
                    ExternalPublishableEventTopics.CLAIM_SUBMITTED,
                    ExternalPublishableEventTopics.CLAIM_STATUS_UPDATED,
                    ExternalPublishableEventTopics.TELECONSULTATION_SCHEDULED,
                    ExternalPublishableEventTopics.TELECONSULTATION_COMPLETED,
                    ExternalPublishableEventTopics.MESSAGE_DELIVERY_UPDATED,
                    ExternalPublishableEventTopics.CONSENT_GRANTED,
                    ExternalPublishableEventTopics.CONSENT_REVOKED,
                    ExternalPublishableEventTopics.PROVIDER_VERIFIED,
                    ExternalPublishableEventTopics.FACILITY_UPDATED
            },
            groupId = "${integration-hub.kafka.external-events.group-id:integration-hub-external-events}"
    )
    public void onExternalPublishableEvent(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        try {
            dispatcher.dispatch(topic, record.key(), record.value());
        } catch (Exception e) {
            log.warn("External publishable dispatch failed for topic={}: {}", topic, e.getMessage());
        }
    }
}
