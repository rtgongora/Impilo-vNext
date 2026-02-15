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
import zw.gov.mohcc.impilo.pipeline.api.dto.EventEnvelope;
import zw.gov.mohcc.impilo.pipeline.api.dto.IngestResponse;
import zw.gov.mohcc.impilo.pipeline.domain.IngestionStatus;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.IngestedEventEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.IngestionSourceEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.IngestedEventRepository;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.IngestionSourceRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IngestionService}.
 */
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock private IngestedEventRepository eventRepository;
    @Mock private IngestionSourceRepository sourceRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private WatermarkService watermarkService;

    private IngestionService ingestionService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        ingestionService = new IngestionService(
                eventRepository, sourceRepository, outboxRepository,
                watermarkService, objectMapper);
    }

    private EventEnvelope createValidEnvelope(String eventId) {
        return new EventEnvelope(
                eventId,
                TENANT_ID.toString(),
                "pct-service",
                "JOURNEY_CREATED",
                "Journey",
                "J-001",
                OffsetDateTime.now(),
                "national",
                "corr-1",
                null,
                Map.of("key", "value"),
                "1.1"
        );
    }

    private IngestionSourceEntity createEnabledSource() {
        IngestionSourceEntity source = new IngestionSourceEntity();
        source.setSourceId("pct-service");
        source.setTenantId(TENANT_ID);
        source.setDisplayName("PCT Service");
        source.setEnabled(true);
        return source;
    }

    @Nested
    @DisplayName("Successful Ingestion")
    class SuccessfulIngestion {

        @Test
        @DisplayName("Valid envelope is ingested and produces ACCEPTED response")
        void validEnvelopeIngested() {
            EventEnvelope envelope = createValidEnvelope("evt-001");
            when(eventRepository.existsByEventId("evt-001")).thenReturn(false);
            when(sourceRepository.findByTenantIdAndSourceId(TENANT_ID, "pct-service"))
                    .thenReturn(Optional.of(createEnabledSource()));
            when(eventRepository.save(any(IngestedEventEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            IngestResponse response = ingestionService.ingest(envelope, TENANT_ID);

            assertThat(response.eventId()).isEqualTo("evt-001");
            assertThat(response.status()).isEqualTo(IngestionStatus.ACCEPTED.name());
            verify(eventRepository).save(any(IngestedEventEntity.class));
            verify(outboxRepository).save(any(EventOutboxEntity.class));
            verify(watermarkService).advance(eq(TENANT_ID), eq("pct-service"),
                    eq("evt-001"), any(OffsetDateTime.class));
        }

        @Test
        @DisplayName("Ingested event entity has correct fields mapped from envelope")
        void entityFieldsMappedCorrectly() {
            EventEnvelope envelope = createValidEnvelope("evt-002");
            when(eventRepository.existsByEventId("evt-002")).thenReturn(false);
            when(sourceRepository.findByTenantIdAndSourceId(TENANT_ID, "pct-service"))
                    .thenReturn(Optional.of(createEnabledSource()));
            when(eventRepository.save(any(IngestedEventEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ingestionService.ingest(envelope, TENANT_ID);

            ArgumentCaptor<IngestedEventEntity> captor = ArgumentCaptor.forClass(IngestedEventEntity.class);
            verify(eventRepository).save(captor.capture());

            IngestedEventEntity saved = captor.getValue();
            assertThat(saved.getEventId()).isEqualTo("evt-002");
            assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(saved.getSourceId()).isEqualTo("pct-service");
            assertThat(saved.getEventType()).isEqualTo("JOURNEY_CREATED");
            assertThat(saved.getAggregateType()).isEqualTo("Journey");
            assertThat(saved.getAggregateId()).isEqualTo("J-001");
            assertThat(saved.getPodId()).isEqualTo("national");
            assertThat(saved.getSchemaVersion()).isEqualTo("1.1");
            assertThat(saved.getIngestionStatus()).isEqualTo(IngestionStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("Duplicate Detection")
    class DuplicateDetection {

        @Test
        @DisplayName("Duplicate eventId returns DUPLICATE status")
        void duplicateEventIdReturnsStatus() {
            EventEnvelope envelope = createValidEnvelope("evt-dup");
            when(eventRepository.existsByEventId("evt-dup")).thenReturn(true);

            IngestResponse response = ingestionService.ingest(envelope, TENANT_ID);

            assertThat(response.status()).isEqualTo(IngestionStatus.DUPLICATE.name());
            verify(eventRepository, never()).save(any());
            verify(outboxRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Source Validation")
    class SourceValidation {

        @Test
        @DisplayName("Unknown source returns REJECTED status")
        void unknownSourceRejected() {
            EventEnvelope envelope = createValidEnvelope("evt-003");
            when(eventRepository.existsByEventId("evt-003")).thenReturn(false);
            when(sourceRepository.findByTenantIdAndSourceId(TENANT_ID, "pct-service"))
                    .thenReturn(Optional.empty());

            IngestResponse response = ingestionService.ingest(envelope, TENANT_ID);

            assertThat(response.status()).isEqualTo(IngestionStatus.REJECTED.name());
            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Disabled source returns REJECTED status")
        void disabledSourceRejected() {
            EventEnvelope envelope = createValidEnvelope("evt-004");
            when(eventRepository.existsByEventId("evt-004")).thenReturn(false);
            IngestionSourceEntity disabled = createEnabledSource();
            disabled.setEnabled(false);
            when(sourceRepository.findByTenantIdAndSourceId(TENANT_ID, "pct-service"))
                    .thenReturn(Optional.of(disabled));

            IngestResponse response = ingestionService.ingest(envelope, TENANT_ID);

            assertThat(response.status()).isEqualTo(IngestionStatus.REJECTED.name());
        }
    }
}
