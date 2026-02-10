package zw.gov.mohcc.impilo.sharedkernel.events;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.sharedkernel.schema.SchemaValidationException;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventEnvelopeTest {

    private EventEnvelope.Builder validBuilder() {
        return EventEnvelope.builder()
                .eventType("vito.patient.created")
                .schemaVersion(1)
                .correlationId("corr-1")
                .causationId("cause-1")
                .idempotencyKey("idem-1")
                .producer("vito-service")
                .tenantId("tenant-zw")
                .podId("pod-harare-central")
                .occurredAt(OffsetDateTime.now())
                .subjectType("Patient")
                .subjectId("CPID-12345")
                .payload(Map.of("given_name_hash", "abc123"));
    }

    @Test
    void builderProducesValidEnvelope() {
        EventEnvelope env = validBuilder().build();
        assertNotNull(env.eventId());
        assertEquals("vito.patient.created", env.eventType());
        assertEquals(1, env.schemaVersion());
        assertNotNull(env.emittedAt());
    }

    @Test
    void schemaVersionZeroThrows() {
        assertThrows(SchemaValidationException.class,
                () -> validBuilder().schemaVersion(0).build());
    }

    @Test
    void schemaVersionNegativeThrows() {
        assertThrows(SchemaValidationException.class,
                () -> validBuilder().schemaVersion(-1).build());
    }

    @Test
    void nullEventTypeThrows() {
        assertThrows(NullPointerException.class,
                () -> validBuilder().eventType(null).build());
    }

    @Test
    void nullCorrelationIdThrows() {
        assertThrows(NullPointerException.class,
                () -> validBuilder().correlationId(null).build());
    }

    @Test
    void nullTenantIdThrows() {
        assertThrows(NullPointerException.class,
                () -> validBuilder().tenantId(null).build());
    }

    @Test
    void nullPodIdThrows() {
        assertThrows(NullPointerException.class,
                () -> validBuilder().podId(null).build());
    }

    @Test
    void nullPayloadThrows() {
        assertThrows(NullPointerException.class,
                () -> validBuilder().payload(null).build());
    }

    @Test
    void metaDefaultsToEmptyMap() {
        EventEnvelope env = validBuilder().meta(null).build();
        assertNotNull(env.meta());
        assertTrue(env.meta().isEmpty());
    }

    @Test
    void payloadIsImmutable() {
        EventEnvelope env = validBuilder().build();
        assertThrows(UnsupportedOperationException.class,
                () -> env.payload().put("hack", "value"));
    }
}
