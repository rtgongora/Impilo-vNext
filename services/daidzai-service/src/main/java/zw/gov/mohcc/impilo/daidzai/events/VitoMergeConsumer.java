package zw.gov.mohcc.impilo.daidzai.events;

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
 * DAIDZAI's VITO identity-merge fan-out consumer. On {@code vito.merge.executed} it repoints every
 * trauma subject anchor DAIDZAI owns via its {@link IdentityRepointHook}s (auto-collected from the
 * Spring context). Two delivery paths share one repoint core:
 * <ul>
 *   <li>a Kafka listener on the VITO merge topic (production; container auto-start controls it);</li>
 *   <li>{@code POST /internal/v1/identity/vito-merge} (broker-less contexts + the runtime-proof rig).</li>
 * </ul>
 * Repointing is idempotent, so redelivery across both paths is safe.
 */
@Component
public class VitoMergeConsumer {

    private static final Logger log = LoggerFactory.getLogger(VitoMergeConsumer.class);

    private final VitoMergeRepointDispatcher dispatcher;

    public VitoMergeConsumer(List<IdentityRepointHook> hooks) {
        this.dispatcher = new VitoMergeRepointDispatcher(hooks);
        log.info("VitoMergeConsumer wired with {} identity repoint hook(s)", dispatcher.hookCount());
    }

    /** Repoint core — one transaction so all of this service's hooks apply atomically. */
    @Transactional
    public Map<String, Integer> apply(String eventPayloadJson) {
        try {
            return dispatcher.dispatch(eventPayloadJson);
        } catch (IllegalArgumentException notAMerge) {
            // The VITO topic also carries unmerge/other events — skip anything that isn't a merge.
            log.debug("skipping non-merge VITO event: {}", notAMerge.getMessage());
            return Map.of();
        }
    }

    @Profile("!test")
    @KafkaListener(topics = {"vito.dedup", "impilo.vito.dedup"}, groupId = "daidzai-vito-merge")
    public void onKafka(String payload) {
        apply(payload);
    }
}
