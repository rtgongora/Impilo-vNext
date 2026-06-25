package zw.gov.mohcc.impilo.oros.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.core.BloodOrderCallbackService;
import zw.gov.mohcc.impilo.oros.persistence.repository.IdempotencyRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the event-driven OROS↔MADI path ({@link MadiBloodEventConsumer}).
 */
@ExtendWith(MockitoExtension.class)
class MadiBloodEventConsumerTest {

    @Mock private BloodOrderCallbackService callbackService;
    @Mock private IdempotencyRepository idempotencyRepository;

    private MadiBloodEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MadiBloodEventConsumer(callbackService, idempotencyRepository, OrosTestObjectMapper.create());
    }

    @Test
    @DisplayName("crossmatch event applies the crossmatch result and is marked processed")
    void crossmatchApplied() {
        String tenant = UUID.randomUUID().toString();
        String msg = "{\"eventId\":\"e1\",\"tenantId\":\"" + tenant + "\",\"eventType\":\"CROSSMATCH_COMPLETED\","
                + "\"payload\":{\"orosOrderRef\":\"ORD-1\",\"result\":\"INCOMPATIBLE\",\"notes\":\"ABO\"}}";
        when(idempotencyRepository.existsById("MADI:e1")).thenReturn(false);

        consumer.consumeBloodOrderEvent(msg);

        verify(callbackService).crossmatchResult("ORD-1", "INCOMPATIBLE", "ABO");
        verify(idempotencyRepository).save(any());
    }

    @Test
    @DisplayName("already-processed event is skipped (idempotent)")
    void idempotentSkip() {
        String msg = "{\"eventId\":\"e1\",\"eventType\":\"BLOOD_ISSUED\",\"payload\":{\"orosOrderRef\":\"ORD-1\",\"unitId\":\"U1\"}}";
        when(idempotencyRepository.existsById("MADI:e1")).thenReturn(true);

        consumer.consumeBloodOrderEvent(msg);

        verifyNoInteractions(callbackService);
        verify(idempotencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("transfusion outcome event applies the outcome")
    void transfusionOutcomeApplied() {
        String msg = "{\"eventId\":\"t1\",\"eventType\":\"TRANSFUSION_COMPLETED\","
                + "\"payload\":{\"orosOrderRef\":\"ORD-9\",\"outcome\":\"STOPPED_ADVERSE\",\"notes\":\"febrile\"}}";
        when(idempotencyRepository.existsById("MADI:t1")).thenReturn(false);

        consumer.consumeTransfusionEvent(msg);

        verify(callbackService).transfusionOutcome("ORD-9", "STOPPED_ADVERSE", "febrile");
    }

    @Test
    @DisplayName("event without an OROS order ref is ignored (not marked processed)")
    void noOrosRefIgnored() {
        String msg = "{\"eventId\":\"e2\",\"eventType\":\"CROSSMATCH_COMPLETED\",\"payload\":{\"result\":\"COMPATIBLE\"}}";
        when(idempotencyRepository.existsById("MADI:e2")).thenReturn(false);

        consumer.consumeBloodOrderEvent(msg);

        verifyNoInteractions(callbackService);
        verify(idempotencyRepository, never()).save(any());
    }
}
