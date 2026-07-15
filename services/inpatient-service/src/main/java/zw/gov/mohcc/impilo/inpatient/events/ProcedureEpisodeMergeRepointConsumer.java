package zw.gov.mohcc.impilo.inpatient.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.inpatient.core.ProcedureEpisodeIdentityRepointService;

/**
 * Wave 5b §15 — consume {@code vito.merge.executed} to repoint theatre cases off a PROVISIONAL trauma
 * patient's CPID onto the reconciled (surviving) CPID. A SEPARATE, procedure-episode-scoped consumer:
 * it does not touch the trauma lane's resuscitation-record repoint hook — both subscribe independently.
 * Best-effort — a repoint failure never blocks any clinical write.
 */
@Component
@Profile("!test")
public class ProcedureEpisodeMergeRepointConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProcedureEpisodeMergeRepointConsumer.class);

    private final ProcedureEpisodeIdentityRepointService repointService;

    public ProcedureEpisodeMergeRepointConsumer(ProcedureEpisodeIdentityRepointService repointService) {
        this.repointService = repointService;
    }

    @KafkaListener(topics = {"vito.merge.executed"}, groupId = "inpatient-service-theatre-repoint")
    public void onMerge(String message) {
        try {
            repointService.handleMergeEvent(message);
        } catch (RuntimeException e) {
            log.warn("INPATIENT-THEATRE: theatre-case merge repoint failed (non-fatal): {}", e.getMessage());
        }
    }
}
