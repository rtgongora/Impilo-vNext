package zw.gov.mohcc.impilo.pct.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes TSHEPO audit stream for PCT-side audit correlation.
 */
@Component
public class TshepoAuditEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(TshepoAuditEventsConsumer.class);

    @KafkaListener(topics = "tshepo.audit.events", groupId = "pct-service-audit")
    public void onAuditEvent(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        log.trace("Received tshepo.audit.events payload for PCT correlation");
    }
}
