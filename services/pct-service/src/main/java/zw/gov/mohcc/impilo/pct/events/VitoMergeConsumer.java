package zw.gov.mohcc.impilo.pct.events;

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
 * PCT's VITO identity-merge fan-out consumer. On {@code vito.merge.executed} it repoints PCT's ED
 * patient anchors via its {@link IdentityRepointHook}s. Kafka listener (prod) + the
 * {@code POST /internal/v1/identity/vito-merge} endpoint (broker-less rig) share one idempotent core.
 */
@Component
public class VitoMergeConsumer {

    private static final Logger log = LoggerFactory.getLogger(VitoMergeConsumer.class);

    private final VitoMergeRepointDispatcher dispatcher;

    public VitoMergeConsumer(List<IdentityRepointHook> hooks) {
        this.dispatcher = new VitoMergeRepointDispatcher(hooks);
        log.info("PCT VitoMergeConsumer wired with {} identity repoint hook(s)", dispatcher.hookCount());
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
    @KafkaListener(topics = {"vito.dedup", "impilo.vito.dedup"}, groupId = "pct-vito-merge")
    public void onKafka(String payload) {
        apply(payload);
    }
}
