package zw.gov.mohcc.impilo.pipeline.events;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.pipeline.api.dto.IngestResponse;
import zw.gov.mohcc.impilo.pipeline.core.BusEventIngestService;
import zw.gov.mohcc.impilo.pipeline.domain.IngestionStatus;

/**
 * Consumes clinical/kernel bus topics into the data pipeline for reporting and analytics feeds.
 */
@Component
public class PipelineEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PipelineEventConsumer.class);

    private final BusEventIngestService busEventIngestService;
    private final MeterRegistry meterRegistry;

    public PipelineEventConsumer(BusEventIngestService busEventIngestService,
                                 MeterRegistry meterRegistry) {
        this.busEventIngestService = busEventIngestService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "#{'${pipeline.kafka.topics.journey-completed:clinical.pct.journey.completed,pct.journey.state_changed}'.split(',')}",
            groupId = "data-pipeline-service")
    public void consumeJourneyCompleted(String message,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        meterRegistry.counter("impilo.eventing.consumer.messages.total",
                "service", "data-pipeline-service", "topic", topic).increment();
        IngestResponse r = busEventIngestService.ingestFromKafka(
                topic, "pct-service", message);
        log.info("{} -> pipeline status={} detail={}",
                topic,
                r.status(), r.message());
        if (IngestionStatus.REJECTED.name().equals(r.status())) {
            meterRegistry.counter("impilo.eventing.consumer.rejected.total",
                    "service", "data-pipeline-service", "topic", topic).increment();
            log.warn("Rejected {} ingest: {}", topic, r.message());
        }
    }

    @KafkaListener(
            topics = "#{'${pipeline.kafka.topics.client-registered:kernel.vito.client.registered,impilo.vito.identity}'.split(',')}",
            groupId = "data-pipeline-service")
    public void consumeClientRegistered(String message,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        meterRegistry.counter("impilo.eventing.consumer.messages.total",
                "service", "data-pipeline-service", "topic", topic).increment();
        IngestResponse r = busEventIngestService.ingestFromKafka(
                topic, "vito-service", message);
        log.info("{} -> pipeline status={}", topic, r.status());
    }

    @KafkaListener(
            topics = "#{'${pipeline.kafka.topics.result-available:clinical.oros.result.available,oros.result.available}'.split(',')}",
            groupId = "data-pipeline-service")
    public void consumeLabResult(String message,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        meterRegistry.counter("impilo.eventing.consumer.messages.total",
                "service", "data-pipeline-service", "topic", topic).increment();
        IngestResponse r = busEventIngestService.ingestFromKafka(
                topic, "oros-service", message);
        log.info("{} -> pipeline status={}", topic, r.status());
    }
}
