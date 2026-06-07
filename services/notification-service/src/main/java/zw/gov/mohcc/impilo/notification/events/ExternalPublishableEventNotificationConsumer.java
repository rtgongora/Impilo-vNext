package zw.gov.mohcc.impilo.notification.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes externally publishable platform events for notification fan-out.
 */
@Component
public class ExternalPublishableEventNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExternalPublishableEventNotificationConsumer.class);

    @KafkaListener(
            topics = {
                    "patient.registered",
                    "patient.updated",
                    "encounter.started",
                    "encounter.closed",
                    "lab.order.created",
                    "lab.result.received",
                    "imaging.order.created",
                    "imaging.report.received",
                    "stock.level.updated",
                    "stock.requisition.created",
                    "payment.initiated",
                    "payment.completed",
                    "claim.submitted",
                    "claim.status.updated",
                    "teleconsultation.scheduled",
                    "teleconsultation.completed",
                    "message.delivery.updated",
                    "consent.granted",
                    "consent.revoked",
                    "provider.verified",
                    "facility.updated"
            },
            groupId = "${notification.kafka.external-events.group-id:notification-service-external-events}"
    )
    public void onExternalPublishableEvent(ConsumerRecord<String, String> record) {
        log.debug("External publishable event received for notification routing: topic={}", record.topic());
    }
}
