package zw.gov.mohcc.impilo.pipeline.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pipeline.api.dto.WatermarkResponse;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.PipelineWatermarkEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.PipelineWatermarkRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-source watermarks that track ingestion progress.
 * Each advance atomically updates the high-water mark and emits
 * a watermark-updated outbox event.
 */
@Service
public class WatermarkService {

    private static final Logger log = LoggerFactory.getLogger(WatermarkService.class);

    private final PipelineWatermarkRepository watermarkRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public WatermarkService(PipelineWatermarkRepository watermarkRepository,
                            EventOutboxRepository outboxRepository,
                            ObjectMapper objectMapper) {
        this.watermarkRepository = watermarkRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Advance the watermark for a source. Creates the watermark if it does not exist.
     */
    @Transactional
    public void advance(UUID tenantId, String sourceId, String eventId, OffsetDateTime occurredAt) {
        PipelineWatermarkEntity watermark = watermarkRepository
                .findByTenantIdAndSourceId(tenantId, sourceId)
                .orElse(null);

        if (watermark == null) {
            watermark = new PipelineWatermarkEntity();
            watermark.setTenantId(tenantId);
            watermark.setSourceId(sourceId);
            watermark.setLastEventId(eventId);
            watermark.setLastOccurredAt(occurredAt);
            watermark.setEventCount(1);
        } else {
            watermark.setLastEventId(eventId);
            watermark.setLastOccurredAt(occurredAt);
            watermark.setEventCount(watermark.getEventCount() + 1);
        }

        watermarkRepository.save(watermark);

        // Emit watermark-updated outbox event
        writeWatermarkOutboxEvent(tenantId, sourceId, eventId, watermark.getEventCount());

        log.debug("Watermark advanced for source={} to event={} (count={})",
                sourceId, eventId, watermark.getEventCount());
    }

    /**
     * Retrieve all watermarks for a tenant.
     */
    public List<WatermarkResponse> getWatermarks(UUID tenantId) {
        return watermarkRepository.findByTenantId(tenantId).stream()
                .map(w -> new WatermarkResponse(
                        w.getSourceId(),
                        w.getTenantId().toString(),
                        w.getLastEventId(),
                        w.getLastOccurredAt(),
                        w.getEventCount(),
                        w.getUpdatedAt()))
                .toList();
    }

    private void writeWatermarkOutboxEvent(UUID tenantId, String sourceId,
                                           String lastEventId, long eventCount) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("PipelineWatermark");
        outbox.setAggregateId(sourceId);
        outbox.setEventType("WATERMARK_UPDATED");
        outbox.setTenantId(tenantId);

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "sourceId", sourceId,
                    "lastEventId", lastEventId,
                    "eventCount", eventCount,
                    "tenantId", tenantId.toString()
            ));
            outbox.setPayload(payload);
        } catch (JsonProcessingException e) {
            outbox.setPayload("{}");
            log.error("Failed to serialize watermark outbox payload for source {}", sourceId, e);
        }

        outboxRepository.save(outbox);
    }
}
