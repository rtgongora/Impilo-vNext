package zw.gov.mohcc.impilo.pct.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes surveillance case-opened events for PCT journey correlation.
 */
@Component
public class SurveillanceCaseOpenedConsumer {

    private static final Logger log = LoggerFactory.getLogger(SurveillanceCaseOpenedConsumer.class);

    @KafkaListener(topics = "impilo.surv.case.opened.v1", groupId = "pct-service")
    public void onCaseOpened(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        log.debug("Received impilo.surv.case.opened.v1 for PCT correlation");
    }
}
