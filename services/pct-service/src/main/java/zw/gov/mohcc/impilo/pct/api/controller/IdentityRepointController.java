package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.events.VitoMergeConsumer;

import java.util.Map;

/**
 * Broker-less delivery of a {@code vito.merge.executed} event to PCT (same repoint core as the Kafka
 * listener). Used by the runtime-proof rig / no-Kafka contexts. Idempotent.
 */
@RestController
@RequestMapping("/internal/v1/identity")
public class IdentityRepointController {

    private final VitoMergeConsumer consumer;

    public IdentityRepointController(VitoMergeConsumer consumer) {
        this.consumer = consumer;
    }

    @PostMapping("/vito-merge")
    public Map<String, Integer> vitoMerge(@RequestBody String payload) {
        return consumer.apply(payload);
    }
}
