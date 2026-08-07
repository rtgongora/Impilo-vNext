package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverride;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * The audit write itself — what actually lands in the outbox when an override happens.
 *
 * <p>Asserted at this level rather than only through the guard because the guard's test uses a
 * capturing fake. This proves the real recorder emits a row the audit plane can read, on the
 * aggregate type that routes to it.</p>
 */
@ExtendWith(MockitoExtension.class)
class MadiUngovernedOverrideRecorderTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-4000-800000000001");

    @Mock private MadiEventEmitter eventEmitter;

    private UngovernedOverride override() {
        return new UngovernedOverride(
                TENANT.toString(), "clinician-7", "O_NEG_EMERGENCY_RELEASE", "HID-42",
                "BREAK_GLASS", "not-a-real-token", "connect timed out after 2000ms",
                UngovernedOverride.DEFAULT_REVIEW_OBLIGATION, Instant.now());
    }

    @Test
    @DisplayName("emits an outbox row on the aggregate type routed to the audit plane")
    @SuppressWarnings("unchecked")
    void emitsOutboxRowShapedForTheAuditPlane() {
        new MadiUngovernedOverrideRecorder(eventEmitter).record(override());

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(eventEmitter).emit(
                eq(MadiUngovernedOverrideRecorder.AGGREGATE_TYPE),
                anyString(),
                eq(UngovernedOverride.EVENT_TYPE),
                eq("PATIENT"),
                eq("HID-42"),
                payload.capture(),
                eq(TENANT));

        Map<String, Object> p = payload.getValue();

        // AuditEventRequest's required fields — without these the Kafka consumer rejects the row
        // and the record never reaches the hash chain.
        assertEquals(TENANT.toString(), p.get("tenantId"));
        assertEquals(UngovernedOverride.EVENT_TYPE, p.get("eventType"));
        assertEquals("clinician-7", p.get("actorId"));
        assertNotNull(p.get("actorType"));
        assertEquals("O_NEG_EMERGENCY_RELEASE", p.get("action"));
        assertNotNull(p.get("correlationId"));

        // The bit that makes it findable as an override rather than ordinary break-glass traffic.
        assertEquals(MadiUngovernedOverrideRecorder.OUTCOME, p.get("outcome"));

        Map<String, Object> detail = (Map<String, Object>) p.get("detail");
        assertEquals(Boolean.FALSE, detail.get("governed"));
        assertEquals("connect timed out after 2000ms", detail.get("grantCheckFailureReason"));
        assertNotNull(detail.get("reviewObligation"));
        // Recorded, but labelled as a claim rather than as evidence.
        assertEquals("BREAK_GLASS", detail.get("purposeOfUseUnverified"));
        assertEquals("not-a-real-token", detail.get("grantTokenUnvalidated"));
    }

    @Test
    @DisplayName("eventType and outcome fit the audit contract's column widths")
    void fieldsFitAuditContractWidths() {
        // AuditEventRequest constrains eventType to 64 and outcome to 16. A value that overflows
        // is rejected at the consumer, which would lose the record silently — the failure mode
        // this whole mechanism exists to avoid.
        assertFalse(UngovernedOverride.EVENT_TYPE.length() > 64);
        assertFalse(MadiUngovernedOverrideRecorder.OUTCOME.length() > 16);
    }

    @Test
    @DisplayName("a failed outbox write propagates — never a silent allow")
    void outboxFailurePropagates() {
        doThrow(new IllegalStateException("db down"))
                .when(eventEmitter).emit(anyString(), anyString(), anyString(), anyString(),
                        anyString(), any(), any());

        assertThrows(IllegalStateException.class,
                () -> new MadiUngovernedOverrideRecorder(eventEmitter).record(override()));
    }
}
