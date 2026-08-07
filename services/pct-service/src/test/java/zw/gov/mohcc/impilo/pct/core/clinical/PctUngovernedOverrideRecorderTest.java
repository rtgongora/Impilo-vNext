package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverride;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * The audit write itself — what actually lands in pct's outbox when the waiver is granted without a
 * validated grant.
 */
@ExtendWith(MockitoExtension.class)
class PctUngovernedOverrideRecorderTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-4000-800000000001");

    @Mock private EventOutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UngovernedOverride override() {
        return new UngovernedOverride(
                TENANT.toString(), "provider-1", "CLINICAL_WRITE_WITHOUT_CARE_CONTEXT", "CPID-1",
                "EMERGENCY", null, "connect timed out after 2000ms",
                UngovernedOverride.DEFAULT_REVIEW_OBLIGATION, Instant.now());
    }

    @Test
    @DisplayName("writes an outbox row on the aggregate type routed to the audit plane")
    void writesOutboxRowShapedForTheAuditPlane() throws Exception {
        new PctUngovernedOverrideRecorder(outboxRepository, objectMapper).record(override());

        ArgumentCaptor<EventOutboxEntity> row = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(row.capture());

        assertEquals(PctUngovernedOverrideRecorder.AGGREGATE_TYPE, row.getValue().getAggregateType());
        assertEquals(UngovernedOverride.EVENT_TYPE, row.getValue().getEventType());
        assertEquals(TENANT, row.getValue().getTenantId());

        Map<String, Object> payload = objectMapper.readValue(row.getValue().getPayload(), Map.class);

        // AuditEventRequest's required fields — without these the Kafka consumer rejects the row
        // and the record never reaches the hash chain.
        assertEquals(TENANT.toString(), payload.get("tenantId"));
        assertEquals(UngovernedOverride.EVENT_TYPE, payload.get("eventType"));
        assertEquals("provider-1", payload.get("actorId"));
        assertNotNull(payload.get("actorType"));
        assertEquals("CLINICAL_WRITE_WITHOUT_CARE_CONTEXT", payload.get("action"));
        assertNotNull(payload.get("correlationId"));

        // Findable as an override rather than as ordinary care traffic.
        assertEquals(PctUngovernedOverrideRecorder.OUTCOME, payload.get("outcome"));

        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) payload.get("detail");
        assertEquals(Boolean.FALSE, detail.get("governed"));
        assertEquals("connect timed out after 2000ms", detail.get("grantCheckFailureReason"));
        assertNotNull(detail.get("reviewObligation"));
        assertEquals("EMERGENCY", detail.get("purposeOfUseUnverified"));
    }

    @Test
    @DisplayName("eventType and outcome fit the audit contract's column widths")
    void fieldsFitAuditContractWidths() {
        // AuditEventRequest constrains eventType to 64 and outcome to 16. Overflow is rejected at
        // the consumer, losing the record silently — the failure mode this exists to avoid.
        assertFalse(UngovernedOverride.EVENT_TYPE.length() > 64);
        assertFalse(PctUngovernedOverrideRecorder.OUTCOME.length() > 16);
    }

    @Test
    @DisplayName("a failed outbox write propagates — never a silent waiver")
    void outboxFailurePropagates() {
        doThrow(new IllegalStateException("db down")).when(outboxRepository).save(any());

        assertThrows(IllegalStateException.class,
                () -> new PctUngovernedOverrideRecorder(outboxRepository, objectMapper).record(override()));
    }
}
