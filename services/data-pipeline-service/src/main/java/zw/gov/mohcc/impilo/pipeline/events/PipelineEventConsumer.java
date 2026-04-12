package zw.gov.mohcc.impilo.pipeline.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
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

    public PipelineEventConsumer(BusEventIngestService busEventIngestService) {
        this.busEventIngestService = busEventIngestService;
    }

    @KafkaListener(topics = "clinical.pct.journey.completed", groupId = "data-pipeline-service")
    public void consumeJourneyCompleted(String message) {
        IngestResponse r = busEventIngestService.ingestFromKafka(
                "clinical.pct.journey.completed", "pct-service", message);
        log.info("clinical.pct.journey.completed -> pipeline status={} detail={}",
                r.status(), r.message());
        if (IngestionStatus.REJECTED.name().equals(r.status())) {
            log.warn("Rejected journey.completed ingest: {}", r.message());
        }
    }

    @KafkaListener(topics = "kernel.vito.client.registered", groupId = "data-pipeline-service")
    public void consumeClientRegistered(String message) {
        IngestResponse r = busEventIngestService.ingestFromKafka(
                "kernel.vito.client.registered", "vito-service", message);
        log.info("kernel.vito.client.registered -> pipeline status={}", r.status());
    }

    @KafkaListener(topics = "clinical.oros.result.available", groupId = "data-pipeline-service")
    public void consumeLabResult(String message) {
        IngestResponse r = busEventIngestService.ingestFromKafka(
                "clinical.oros.result.available", "oros-service", message);
        log.info("clinical.oros.result.available -> pipeline status={}", r.status());
    }
}
