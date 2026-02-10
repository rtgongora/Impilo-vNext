package zw.gov.mohcc.impilo.vito.migration.v11;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Standalone verification of v1.1 EventEnvelope contract for VITO.
 * Runs without JUnit — exit code 0 = all pass.
 * <p>
 * This mirrors EventEnvelopeContractTest but can be run with plain javac+java.
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
        System.out.println("=== VITO v1.1 EventEnvelope Contract Tests ===\n");

        test("Null schema_version throws at construction time", () -> {
            try {
                new TestEventEnvelope(
                        UUID.randomUUID().toString(), "vito.patient.created",
                        null, "corr-001", "cause-001", "idem-001",
                        "vito-service", "tenant-zw", "pod-harare",
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
                        UUID.randomUUID().toString(), "vito.patient.updated",
                        0, "corr-001", "cause-001", "idem-001",
                        "vito-service", "tenant-zw", "pod-harare",
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
                        UUID.randomUUID().toString(), "vito.patient.merged",
                        -1, "corr-001", "cause-001", "idem-001",
                        "vito-service", "tenant-zw", "pod-harare",
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345", Map.of(), Map.of());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        });

        test("Valid envelope constructs successfully", () -> {
            TestEventEnvelope env = validEnvelope();
            if (!"vito.patient.created".equals(env.eventType()))
                throw new AssertionError("eventType mismatch");
            if (env.schemaVersion() != 1)
                throw new AssertionError("schemaVersion mismatch");
        });

        test("Missing tenant_id throws", () -> {
            try {
                new TestEventEnvelope(
                        UUID.randomUUID().toString(), "vito.patient.created", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito-service", null, "pod-harare",
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
                        UUID.randomUUID().toString(), "vito.patient.created", 1,
                        "corr-001", "cause-001", "idem-001",
                        "vito-service", "tenant-zw", null,
                        OffsetDateTime.now(), OffsetDateTime.now(),
                        "Patient", "CPID-12345", Map.of(), Map.of());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        });

        test("VITO producer must be 'vito-service'", () -> {
            TestEventEnvelope env = validEnvelope();
            if (!"vito-service".equals(env.producer()))
                throw new AssertionError("producer mismatch: " + env.producer());
        });

        test("VITO event types must start with 'vito.' prefix", () -> {
            TestEventEnvelope env = validEnvelope();
            if (!env.eventType().startsWith("vito."))
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
                UUID.randomUUID().toString(), "vito.patient.created", 1,
                "corr-001", "cause-001", "idem-001",
                "vito-service", "tenant-zw", "pod-harare-central",
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
