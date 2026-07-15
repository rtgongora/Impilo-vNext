package zw.gov.mohcc.impilo.inpatient.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.inpatient.events.VitoMergeConsumer;

import java.util.Map;

/**
 * Broker-less delivery of a {@code vito.merge.executed} event to inpatient (same repoint core as the
 * Kafka listener) — used by the runtime-proof rig / no-Kafka contexts. Fans out to BOTH the
 * resuscitation and theatre procedure-episode repoint hooks. Idempotent.
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
