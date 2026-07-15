package zw.gov.mohcc.impilo.daidzai.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.daidzai.events.VitoMergeConsumer;

import java.util.Map;

/**
 * Broker-less delivery of a {@code vito.merge.executed} event (the same repoint core the Kafka
 * listener runs). Used by the runtime-proof rig and any no-Kafka context: the raw VITO merge payload
 * is posted here and DAIDZAI repoints its trauma subject anchors. Idempotent.
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
