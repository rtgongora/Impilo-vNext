package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.vito.events.persistence.ControlChannelWatermarkEntity;
import zw.gov.mohcc.impilo.vito.events.persistence.ControlChannelWatermarkRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Wave 21 — Tests for VITO's federation pod revocation consumer.
 *
 * Proves:
 * - Pod revocation event adds pod to local deny list
 * - Idempotent: duplicate revocation events are skipped
 * - Watermark recorded after processing
 * - Malformed events handled gracefully
 * - isDenied() returns correct state
 */
@DisplayName("Wave 21 — VITO Federation Pod Revocation Consumer")
class FederationPodRevocationConsumerTest {

    private ControlChannelWatermarkRepository watermarkRepository;
    private FederationPodRevocationConsumer consumer;

    @BeforeEach
    void setUp() {
        watermarkRepository = mock(ControlChannelWatermarkRepository.class);
        consumer = new FederationPodRevocationConsumer(watermarkRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("Pod revocation adds pod to deny list")
    void podRevocationAddsToDenyList() {
        when(watermarkRepository.existsByEventId(any())).thenReturn(false);

        String event = """
                {
                    "pod_id": "pod-harare-central",
                    "revoked_by": "admin",
                    "reason": "SECURITY_BREACH",
                    "priority": "HIGH"
                }
                """;

        consumer.consumePodRevocationEvent(event);

        assertThat(consumer.isDenied("pod-harare-central")).isTrue();
        verify(watermarkRepository).save(argThat(w ->
                "pod-revoked-pod-harare-central".equals(w.getEventId()) &&
                        "POD_REVOKED".equals(w.getEventType())));
    }

    @Test
    @DisplayName("Duplicate revocation event is idempotent")
    void duplicateRevocationIdempotent() {
        when(watermarkRepository.existsByEventId("pod-revoked-pod-x")).thenReturn(true);

        String event = """
                {
                    "pod_id": "pod-x",
                    "revoked_by": "admin",
                    "reason": "TEST"
                }
                """;

        consumer.consumePodRevocationEvent(event);

        verify(watermarkRepository, never()).save(any(ControlChannelWatermarkEntity.class));
    }

    @Test
    @DisplayName("Missing pod_id is skipped gracefully")
    void missingPodIdSkipped() {
        String event = """
                {
                    "revoked_by": "admin",
                    "reason": "TEST"
                }
                """;

        consumer.consumePodRevocationEvent(event);

        verify(watermarkRepository, never()).save(any());
    }

    @Test
    @DisplayName("Malformed JSON handled gracefully")
    void malformedJsonHandled() {
        consumer.consumePodRevocationEvent("not json {{");

        verify(watermarkRepository, never()).save(any());
    }

    @Test
    @DisplayName("isDenied returns false for non-revoked pod")
    void isDeniedFalseForActive() {
        assertThat(consumer.isDenied("active-pod")).isFalse();
    }

    @Test
    @DisplayName("clearDenial removes pod from deny list")
    void clearDenialRemovesPod() {
        when(watermarkRepository.existsByEventId(any())).thenReturn(false);

        consumer.consumePodRevocationEvent("""
                {"pod_id": "pod-to-reinstate", "revoked_by": "admin", "reason": "TEST"}
                """);

        assertThat(consumer.isDenied("pod-to-reinstate")).isTrue();

        consumer.clearDenial("pod-to-reinstate");

        assertThat(consumer.isDenied("pod-to-reinstate")).isFalse();
    }
}
