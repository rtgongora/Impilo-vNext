package zw.gov.mohcc.impilo.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminAuditEmitterTest {

    private InMemoryAdminAuditSink sink;
    private AdminAuditEmitter emitter;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        sink = new InMemoryAdminAuditSink();
        emitter = new AdminAuditEmitter("tshepo", sink);
    }

    @Test
    void emitsAuditEventOnProtectedEndpoint() throws Exception {
        emitter.emit(
                "POLICY_UPDATE",
                "admin-user-123",
                "USER",
                "tenant-1",
                "pod-central",
                "corr-abc-123",
                "Policy",
                "policy-42",
                Map.of("old_version", "v1", "new_version", "v2")
        );

        List<AdminAuditRecord> records = sink.getRecords();
        assertEquals(1, records.size(), "Exactly one audit event should be emitted");

        AdminAuditRecord record = records.get(0);
        assertEquals("AdminAudit", record.aggregateType());
        assertEquals("policy-42", record.aggregateId());
        assertTrue(record.eventType().startsWith("impilo.tshepo.admin_audit."));
        assertTrue(record.eventType().contains("policy_update"));
        assertNotNull(record.eventId());
        assertNotNull(record.createdAt());

        // Verify payload is valid JSON containing the EventEnvelope
        var payload = mapper.readTree(record.payload());
        assertEquals("tshepo", payload.get("producer").asText());
        assertEquals("tenant-1", payload.get("tenantId").asText());
        assertEquals("corr-abc-123", payload.get("correlationId").asText());

        // Verify immutable flag
        var eventPayload = payload.get("payload");
        assertTrue(eventPayload.get("immutable").asBoolean());
        assertEquals("POLICY_UPDATE", eventPayload.get("action").asText());
        assertEquals("admin-user-123", eventPayload.get("actor_id").asText());
    }

    @Test
    void emitsMultipleAuditEvents() {
        emitter.emit("ROLE_GRANT", "admin-1", "USER", "t1", "p1", "c1", "Role", "role-1", Map.of());
        emitter.emit("ROLE_REVOKE", "admin-1", "USER", "t1", "p1", "c2", "Role", "role-1", Map.of());

        assertEquals(2, sink.getRecords().size());
        assertNotEquals(
                sink.getRecords().get(0).eventId(),
                sink.getRecords().get(1).eventId(),
                "Each event must have a unique ID"
        );
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThrows(NullPointerException.class, () ->
                emitter.emit(null, "actor", "USER", "t", "p", "c", "S", "s", Map.of()));
        assertThrows(NullPointerException.class, () ->
                emitter.emit("ACT", null, "USER", "t", "p", "c", "S", "s", Map.of()));
        assertThrows(NullPointerException.class, () ->
                emitter.emit("ACT", "actor", "USER", null, "p", "c", "S", "s", Map.of()));
        assertThrows(NullPointerException.class, () ->
                emitter.emit("ACT", "actor", "USER", "t", "p", null, "S", "s", Map.of()));
    }

    @Test
    void handlesNullOptionalFields() {
        assertDoesNotThrow(() ->
                emitter.emit("ACT", "actor", null, "t", null, "c", null, null, null));
        assertEquals(1, sink.getRecords().size());
    }

    @Test
    void clearResetsRecords() {
        emitter.emit("ACT", "actor", "USER", "t", "p", "c", "S", "s", Map.of());
        assertEquals(1, sink.getRecords().size());
        sink.clear();
        assertEquals(0, sink.getRecords().size());
    }
}
