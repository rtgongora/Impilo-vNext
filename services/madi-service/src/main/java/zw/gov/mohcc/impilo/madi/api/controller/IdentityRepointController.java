package zw.gov.mohcc.impilo.madi.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointHook;
import zw.gov.mohcc.impilo.sharedkernel.identity.VitoMergeRepointDispatcher;

import java.util.List;
import java.util.Map;

/**
 * MADI identity-merge fan-out. MADI has no Kafka consumer today, so delivery is via this internal
 * endpoint (the runtime-proof rig + any no-Kafka context post the raw {@code vito.merge.executed}
 * payload here); a Kafka listener is a follow-up when MADI gains a consumer. The repoint core (the
 * shared dispatcher over MADI's {@link IdentityRepointHook}s) is identical to the other services.
 */
@RestController
@RequestMapping("/internal/v1/identity")
public class IdentityRepointController {

    private static final Logger log = LoggerFactory.getLogger(IdentityRepointController.class);

    private final VitoMergeRepointDispatcher dispatcher;

    public IdentityRepointController(List<IdentityRepointHook> hooks) {
        this.dispatcher = new VitoMergeRepointDispatcher(hooks);
        log.info("MADI identity-repoint fan-out wired with {} hook(s)", dispatcher.hookCount());
    }

    @PostMapping("/vito-merge")
    @Transactional
    public Map<String, Integer> vitoMerge(@RequestBody String payload) {
        try {
            return dispatcher.dispatch(payload);
        } catch (IllegalArgumentException notAMerge) {
            return Map.of();
        }
    }
}
