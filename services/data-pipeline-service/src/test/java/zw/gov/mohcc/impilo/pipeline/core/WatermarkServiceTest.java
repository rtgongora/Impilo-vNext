package zw.gov.mohcc.impilo.pipeline.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pipeline.api.dto.WatermarkResponse;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.PipelineWatermarkEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.PipelineWatermarkRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WatermarkService}.
 */
@ExtendWith(MockitoExtension.class)
class WatermarkServiceTest {

    @Mock private PipelineWatermarkRepository watermarkRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private WatermarkService watermarkService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        watermarkService = new WatermarkService(watermarkRepository, outboxRepository, objectMapper);
    }

    @Nested
    @DisplayName("Watermark Advance")
    class WatermarkAdvance {

        @Test
        @DisplayName("Creates new watermark when none exists for source")
        void createsNewWatermark() {
            when(watermarkRepository.findByTenantIdAndSourceId(TENANT_ID, "pct-service"))
                    .thenReturn(Optional.empty());
            when(watermarkRepository.save(any(PipelineWatermarkEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            OffsetDateTime now = OffsetDateTime.now();
            watermarkService.advance(TENANT_ID, "pct-service", "evt-001", now);

            ArgumentCaptor<PipelineWatermarkEntity> captor =
                    ArgumentCaptor.forClass(PipelineWatermarkEntity.class);
            verify(watermarkRepository).save(captor.capture());

            PipelineWatermarkEntity saved = captor.getValue();
            assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(saved.getSourceId()).isEqualTo("pct-service");
            assertThat(saved.getLastEventId()).isEqualTo("evt-001");
            assertThat(saved.getEventCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Increments existing watermark count and updates last event")
        void incrementsExistingWatermark() {
            PipelineWatermarkEntity existing = new PipelineWatermarkEntity();
            existing.setTenantId(TENANT_ID);
            existing.setSourceId("pct-service");
            existing.setLastEventId("evt-001");
            existing.setEventCount(5);
            existing.setLastOccurredAt(OffsetDateTime.now().minusHours(1));

            when(watermarkRepository.findByTenantIdAndSourceId(TENANT_ID, "pct-service"))
                    .thenReturn(Optional.of(existing));
            when(watermarkRepository.save(any(PipelineWatermarkEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            OffsetDateTime now = OffsetDateTime.now();
            watermarkService.advance(TENANT_ID, "pct-service", "evt-006", now);

            ArgumentCaptor<PipelineWatermarkEntity> captor =
                    ArgumentCaptor.forClass(PipelineWatermarkEntity.class);
            verify(watermarkRepository).save(captor.capture());

            PipelineWatermarkEntity saved = captor.getValue();
            assertThat(saved.getLastEventId()).isEqualTo("evt-006");
            assertThat(saved.getEventCount()).isEqualTo(6);
        }

        @Test
        @DisplayName("Emits WATERMARK_UPDATED outbox event on advance")
        void emitsOutboxEvent() {
            when(watermarkRepository.findByTenantIdAndSourceId(TENANT_ID, "pct-service"))
                    .thenReturn(Optional.empty());
            when(watermarkRepository.save(any(PipelineWatermarkEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            watermarkService.advance(TENANT_ID, "pct-service", "evt-001", OffsetDateTime.now());

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());

            EventOutboxEntity outbox = captor.getValue();
            assertThat(outbox.getEventType()).isEqualTo("WATERMARK_UPDATED");
            assertThat(outbox.getAggregateType()).isEqualTo("PipelineWatermark");
            assertThat(outbox.getAggregateId()).isEqualTo("pct-service");
            assertThat(outbox.getTenantId()).isEqualTo(TENANT_ID);
        }
    }

    @Nested
    @DisplayName("Watermark Retrieval")
    class WatermarkRetrieval {

        @Test
        @DisplayName("Returns all watermarks for a tenant")
        void returnsAllWatermarks() {
            PipelineWatermarkEntity wm1 = new PipelineWatermarkEntity();
            wm1.setTenantId(TENANT_ID);
            wm1.setSourceId("pct-service");
            wm1.setLastEventId("evt-10");
            wm1.setLastOccurredAt(OffsetDateTime.now());
            wm1.setEventCount(10);
            wm1.setUpdatedAt(OffsetDateTime.now());

            PipelineWatermarkEntity wm2 = new PipelineWatermarkEntity();
            wm2.setTenantId(TENANT_ID);
            wm2.setSourceId("oros-service");
            wm2.setLastEventId("evt-5");
            wm2.setLastOccurredAt(OffsetDateTime.now());
            wm2.setEventCount(5);
            wm2.setUpdatedAt(OffsetDateTime.now());

            when(watermarkRepository.findByTenantId(TENANT_ID))
                    .thenReturn(List.of(wm1, wm2));

            List<WatermarkResponse> result = watermarkService.getWatermarks(TENANT_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).sourceId()).isEqualTo("pct-service");
            assertThat(result.get(0).eventCount()).isEqualTo(10);
            assertThat(result.get(1).sourceId()).isEqualTo("oros-service");
            assertThat(result.get(1).eventCount()).isEqualTo(5);
        }
    }
}
