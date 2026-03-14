package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.vito.events.persistence.ControlChannelWatermarkEntity;
import zw.gov.mohcc.impilo.vito.events.persistence.ControlChannelWatermarkRepository;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link RevocationControlChannelConsumer} verifying:
 * - Events are processed correctly by type
 * - Idempotency: duplicate events are skipped
 * - Malformed events are handled gracefully
 * - Watermark is recorded after processing
 */
class RevocationControlChannelConsumerTest {

    private ControlChannelWatermarkRepository watermarkRepository;
    private RevocationControlChannelConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        watermarkRepository = mock(ControlChannelWatermarkRepository.class);
        objectMapper = new ObjectMapper();
        consumer = new RevocationControlChannelConsumer(watermarkRepository, objectMapper);
    }

    @Test
    @DisplayName("Processes CONSENT_REVOKED event and saves watermark")
    void processConsentRevoked() {
        when(watermarkRepository.existsByEventId("evt-001")).thenReturn(false);

        String event = """
                {
                    "event_type": "CONSENT_REVOKED",
                    "event_id": "evt-001",
                    "tenant_id": "t-1",
                    "subject_id": "patient-123",
                    "consent_id": "consent-abc",
                    "revoked_by": "admin",
                    "reason": "PATIENT_WITHDRAWAL",
                    "correlation_id": "corr-1"
                }
                """;

        consumer.consumeRevocationEvent(event);

        verify(watermarkRepository).save(argThat(w ->
                "evt-001".equals(w.getEventId()) &&
                        "CONSENT_REVOKED".equals(w.getEventType()) &&
                        "patient-123".equals(w.getSubjectId())
        ));
    }

    @Test
    @DisplayName("Processes IDENTITY_MERGED event and saves watermark")
    void processIdentityMerged() {
        when(watermarkRepository.existsByEventId("evt-002")).thenReturn(false);

        String event = """
                {
                    "event_type": "IDENTITY_MERGED",
                    "event_id": "evt-002",
                    "tenant_id": "t-1",
                    "subject_id": "patient-old",
                    "merged_into_id": "patient-new",
                    "correlation_id": "corr-2"
                }
                """;

        consumer.consumeRevocationEvent(event);

        verify(watermarkRepository).save(argThat(w ->
                "evt-002".equals(w.getEventId()) &&
                        "IDENTITY_MERGED".equals(w.getEventType())
        ));
    }

    @Test
    @DisplayName("Processes IDENTITY_DEACTIVATED event and saves watermark")
    void processIdentityDeactivated() {
        when(watermarkRepository.existsByEventId("evt-003")).thenReturn(false);

        String event = """
                {
                    "event_type": "IDENTITY_DEACTIVATED",
                    "event_id": "evt-003",
                    "tenant_id": "t-1",
                    "subject_id": "patient-deact",
                    "correlation_id": "corr-3"
                }
                """;

        consumer.consumeRevocationEvent(event);

        verify(watermarkRepository).save(argThat(w ->
                "evt-003".equals(w.getEventId()) &&
                        "IDENTITY_DEACTIVATED".equals(w.getEventType())
        ));
    }

    @Test
    @DisplayName("Duplicate event is skipped (idempotent)")
    void duplicateEventSkipped() {
        when(watermarkRepository.existsByEventId("evt-dup")).thenReturn(true);

        String event = """
                {
                    "event_type": "CONSENT_REVOKED",
                    "event_id": "evt-dup",
                    "tenant_id": "t-1",
                    "subject_id": "patient-123",
                    "correlation_id": "corr-dup"
                }
                """;

        consumer.consumeRevocationEvent(event);

        // Watermark should NOT be saved again
        verify(watermarkRepository, never()).save(any(ControlChannelWatermarkEntity.class));
    }

    @Test
    @DisplayName("Malformed JSON is handled gracefully")
    void malformedJsonHandled() {
        consumer.consumeRevocationEvent("not valid json {{{");

        verify(watermarkRepository, never()).save(any());
    }

    @Test
    @DisplayName("Missing event_id skips processing")
    void missingEventIdSkips() {
        String event = """
                {
                    "event_type": "CONSENT_REVOKED",
                    "subject_id": "patient-123"
                }
                """;

        consumer.consumeRevocationEvent(event);

        verify(watermarkRepository, never()).save(any());
    }

    @Test
    @DisplayName("Unknown event type is acknowledged but processed with watermark")
    void unknownEventTypeAcknowledged() {
        when(watermarkRepository.existsByEventId("evt-unknown")).thenReturn(false);

        String event = """
                {
                    "event_type": "SOME_FUTURE_EVENT",
                    "event_id": "evt-unknown",
                    "tenant_id": "t-1",
                    "subject_id": "patient-x",
                    "correlation_id": "corr-x"
                }
                """;

        consumer.consumeRevocationEvent(event);

        // Watermark should still be saved to prevent reprocessing
        verify(watermarkRepository).save(argThat(w ->
                "evt-unknown".equals(w.getEventId())
        ));
    }
}
