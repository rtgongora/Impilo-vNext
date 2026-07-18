package zw.gov.mohcc.impilo.inpatient.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointHook;
import zw.gov.mohcc.impilo.sharedkernel.identity.VitoMergeRepointDispatcher;

import java.util.List;
import java.util.Map;

/**
 * Inpatient's subject repoint consumer (Identity Contract §7.3). On merge/reconcile subject events it repoints every
 * inpatient trauma anchor via the Spring-collected {@link IdentityRepointHook}s — which include BOTH
 * the trauma lane's resuscitation hook (emergency_activation.subject_cpid) AND the theatre session's
 * InpatientProcedureEpisodeRepointHook (procedure_episode.subject_cpid), auto-wired into one fan-out.
 * Kafka listener (prod) + the {@code POST /internal/v1/identity/vito-merge} endpoint (broker-less rig)
 * share one idempotent core.
 */
@Component
public class VitoMergeConsumer {

    private static final Logger log = LoggerFactory.getLogger(VitoMergeConsumer.class);

    private final VitoMergeRepointDispatcher dispatcher;

    public VitoMergeConsumer(List<IdentityRepointHook> hooks) {
        this.dispatcher = new VitoMergeRepointDispatcher(hooks);
        log.info("Inpatient VitoMergeConsumer wired with {} identity repoint hook(s)", dispatcher.hookCount());
    }

    @Transactional
    public Map<String, Integer> apply(String eventPayloadJson) {
        try {
            return dispatcher.dispatch(eventPayloadJson);
        } catch (IllegalArgumentException notAMerge) {
            log.debug("skipping non-merge VITO event: {}", notAMerge.getMessage());
            return Map.of();
        }
    }

    @Profile("!test")
    @KafkaListener(topics = "impilo.identity.subject", groupId = "inpatient-vito-merge")
    public void onKafka(String payload) {
        apply(payload);
    }
}
