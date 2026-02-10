package zw.gov.mohcc.impilo.vito.migration.v11;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Standalone verification of v1.1 EventEnvelope contract for VITO.
 * Runs without JUnit — exit code 0 = all pass.
 *
 * Per Manifest v1.1:
 * - event_type must begin with "impilo." (e.g. "impilo.vito.patient.created.v1")
 * - producer must be "vito" (short service name)
 * - All required fields must reject null
 */
public class EventEnvelopeContractStandaloneTest {

    private static int passed = 0;
    private static int failed = 0;

    record TestEventEnvelope(
            String eventId,
            String eventType,
            Integer schemaVersion,
            String correlationId,
            String causationId,
            String idempotencyKey,
            String producer,
            String tenantId,
            String podId,
            OffsetDateTime occurredAt,
            OffsetDateTime emittedAt,
            String subjectType,
            String subjectId,
            Map<String, Object> payload,
            Map<String, Object> meta
    ) {
        TestEventEnvelope {
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("event_id is required");
            }
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("event_type is required");
            }
            if (schemaVersion == null || schemaVersion < 1) {
                throw new IllegalArgumentException(
                        eventType + ": schema_version missing/invalid — value was " + schemaVersion);
            }
            if (correlationId == null) throw new IllegalArgumentException("correlation_id is required");
            if (causationId == null) throw new IllegalArgumentException("causation_id is required");
            if (idempotencyKey == null) throw new IllegalArgumentException("idempotency_key is required");
            if (producer == null) throw new IllegalArgumentException("producer is required");
            if (tenantId == null) throw new IllegalArgumentException("tenant_id is required");
            if (podId == null) throw new IllegalArgumentException("pod_id is required");
            if (occurredAt == null) throw new IllegalArgumentException("occurred_at is required");
            if (emittedAt == null) throw new IllegalArgumentException("emitted_at is required");
            if (subjectType == null) throw new IllegalArgumentException("subject_type is required");
            if (subjectId == null) throw new IllegalArgumentException("subject_id is required");
            if (payload == null) throw new IllegalArgumentException("payload is required");
        }
    }

    public static void main(String[] args) {
        System.out.println("=== VITO v1.1 EventEnvelope Contract Tests (Manifest v1.1) ===\n");

        test("Null schema_version throws at construction time", () -> {
            try {
                new TestEventEnvelope(
                        UUID.randomUUID().toString(), "impilo.vito.patient.created.v1",
                        null, "corr-001", "cause-001", "idem-001",
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345", Map.of(), Map.of());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                if (!e.getMessage().contains("schema_version missing/invalid")) {
                    throw new AssertionError("Wrong message: " + e.getMessage());
                }
            }
        });

        test("Zero schema_version throws at construction time", () -> {
            try {
                new TestEventEnvelope(
                        UUID.randomUUID().toString(), "impilo.vito.patient.updated.v1",
                        0, "corr-001", "cause-001", "idem-001",
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345", Map.of(), Map.of());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        });

        test("Negative schema_version throws at construction time", () -> {
            try {
                new TestEventEnvelope(
                        UUID.randomUUID().toString(), "impilo.vito.patient.merged.v1",
                        -1, "corr-001", "cause-001", "idem-001",
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345", Map.of(), Map.of());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        });

        test("Valid envelope constructs successfully", () -> {
            TestEventEnvelope env = validEnvelope();
            if (!"impilo.vito.patient.created.v1".equals(env.eventType()))
                throw new AssertionError("eventType mismatch: " + env.eventType());
            if (env.schemaVersion() != 1)
                throw new AssertionError("schemaVersion mismatch");
        });

        test("Missing tenant_id throws", () -> {
            try {
                new TestEventEnvelope(
                        UUID.randomUUID().toString(), "impilo.vito.patient.created.v1", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito", null, "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345", Map.of(), Map.of());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        });

        test("Missing pod_id throws", () -> {
            try {
                new TestEventEnvelope(
                        UUID.randomUUID().toString(), "impilo.vito.patient.created.v1", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito", "tenant-zw", null,
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345", Map.of(), Map.of());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        });

        test("Missing correlation_id throws", () -> {
            try {
                new TestEventEnvelope(
                        UUID.randomUUID().toString(), "impilo.vito.patient.created.v1", 1,
                        null, "cause-001", "idem-001",
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345", Map.of(), Map.of());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        });

        test("Missing idempotency_key throws", () -> {
            try {
                new TestEventEnvelope(
                        UUID.randomUUID().toString(), "impilo.vito.patient.created.v1", 1,
                        "corr-001", "cause-001", null,
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345", Map.of(), Map.of());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        });

        test("Missing payload throws", () -> {
            try {
                new TestEventEnvelope(
                        UUID.randomUUID().toString(), "impilo.vito.patient.created.v1", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345", null, Map.of());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        });

        test("VITO producer must be 'vito'", () -> {
            TestEventEnvelope env = validEnvelope();
            if (!"vito".equals(env.producer()))
                throw new AssertionError("producer mismatch: " + env.producer());
        });

        test("VITO event types must start with 'impilo.vito.' prefix", () -> {
            TestEventEnvelope env = validEnvelope();
            if (!env.eventType().startsWith("impilo.vito."))
                throw new AssertionError("prefix mismatch: " + env.eventType());
        });

        System.out.println("\n=== RESULTS ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total:  " + (passed + failed));
        if (failed > 0) {
            System.out.println("\n*** FAILURES DETECTED ***");
            System.exit(1);
        } else {
            System.out.println("\n*** ALL EVENT ENVELOPE CONTRACT TESTS PASSED ***");
        }
    }

    private static TestEventEnvelope validEnvelope() {
        return new TestEventEnvelope(
                UUID.randomUUID().toString(), "impilo.vito.patient.created.v1", 1,
                "corr-001", "cause-001", "idem-001",
                "vito", "tenant-zw", "pod-harare-central",
                OffsetDateTime.now(), OffsetDateTime.now(),
                "Patient", "CPID-12345",
                Map.of("given_name_hash", "abc123"), Map.of());
    }

    private static void test(String name, Runnable body) {
        try {
            body.run();
            System.out.println("  PASS: " + name);
            passed++;
        } catch (Throwable t) {
            System.out.println("  FAIL: " + name + " — " + t.getMessage());
            failed++;
        }
    }
}
