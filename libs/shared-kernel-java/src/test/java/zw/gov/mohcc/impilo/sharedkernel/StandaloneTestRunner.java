package zw.gov.mohcc.impilo.sharedkernel;

import zw.gov.mohcc.impilo.sharedkernel.audit.*;
import zw.gov.mohcc.impilo.sharedkernel.consistency.*;
import zw.gov.mohcc.impilo.sharedkernel.events.*;
import zw.gov.mohcc.impilo.sharedkernel.schema.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standalone test runner for environments without JUnit on classpath.
 * Validates all shared-kernel-java contracts. Exit code 0 = all pass.
 */
public class StandaloneTestRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== shared-kernel-java Standalone Test Suite ===\n");

        // SchemaValidator tests
        test("SchemaValidator: null schema_version throws", () -> {
            SchemaValidator v = new SchemaValidator();
            try {
                v.requireSchemaVersion(null, "test.event");
                throw new AssertionError("Expected SchemaValidationException");
            } catch (SchemaValidationException e) {
                assertContains(e.getMessage(), "schema_version missing/invalid");
            }
        });

        test("SchemaValidator: zero schema_version throws", () -> {
            SchemaValidator v = new SchemaValidator();
            try {
                v.requireSchemaVersion(0, "test.event");
                throw new AssertionError("Expected SchemaValidationException");
            } catch (SchemaValidationException e) {
                assertContains(e.getMessage(), "schema_version missing/invalid");
            }
        });

        test("SchemaValidator: valid schema_version passes", () -> {
            SchemaValidator v = new SchemaValidator();
            v.requireSchemaVersion(1, "test.event");
            v.requireSchemaVersion(42, "test.event");
        });

        // DualEmitPolicy tests
        test("DualEmitPolicy: default is DUAL when unset", () -> {
            System.clearProperty("EMIT_MODE");
            DualEmitPolicy p = new DualEmitPolicy();
            assertEquals(EmitMode.DUAL, p.mode(), "mode");
            assertTrue(p.emitLegacy(), "emitLegacy");
            assertTrue(p.emitV11(), "emitV11");
        });

        test("DualEmitPolicy: respects LEGACY_ONLY", () -> {
            System.setProperty("EMIT_MODE", "LEGACY_ONLY");
            try {
                DualEmitPolicy p = new DualEmitPolicy();
                assertEquals(EmitMode.LEGACY_ONLY, p.mode(), "mode");
                assertTrue(p.emitLegacy(), "emitLegacy");
                assertFalse(p.emitV11(), "emitV11");
            } finally {
                System.clearProperty("EMIT_MODE");
            }
        });

        test("DualEmitPolicy: respects V1_1_ONLY", () -> {
            System.setProperty("EMIT_MODE", "V1_1_ONLY");
            try {
                DualEmitPolicy p = new DualEmitPolicy();
                assertEquals(EmitMode.V1_1_ONLY, p.mode(), "mode");
                assertFalse(p.emitLegacy(), "emitLegacy");
                assertTrue(p.emitV11(), "emitV11");
            } finally {
                System.clearProperty("EMIT_MODE");
            }
        });

        // ConsistencyGate tests
        test("ConsistencyGate: MSIKA_UPDATE_TARIFF requires sync + NATIONAL_SPINE_ONLY", () -> {
            ConsistencyGate gate = new ConsistencyGate();
            ConsistencyDecision d = gate.evaluate(ActionType.MSIKA_UPDATE_TARIFF, "pod-1");
            assertTrue(d.syncRequired(), "syncRequired");
            assertEquals("NATIONAL_SPINE_ONLY", d.authority(), "authority");
        });

        test("ConsistencyGate: VITO_PATIENT_MERGE requires sync + NATIONAL_SPINE_ONLY", () -> {
            ConsistencyGate gate = new ConsistencyGate();
            ConsistencyDecision d = gate.evaluate(ActionType.VITO_PATIENT_MERGE, "pod-2");
            assertTrue(d.syncRequired(), "syncRequired");
            assertEquals("NATIONAL_SPINE_ONLY", d.authority(), "authority");
        });

        test("ConsistencyGate: CLINICAL_PRESCRIBE requires sync + KERNEL_PDP_REQUIRED", () -> {
            ConsistencyGate gate = new ConsistencyGate();
            ConsistencyDecision d = gate.evaluate(
                    ActionType.CLINICAL_PRESCRIBE_CONTROLLED_SUBSTANCE, "pod-3");
            assertTrue(d.syncRequired(), "syncRequired");
            assertEquals("KERNEL_PDP_REQUIRED", d.authority(), "authority");
        });

        test("ConsistencyGate: UNKNOWN does not require sync", () -> {
            ConsistencyGate gate = new ConsistencyGate();
            ConsistencyDecision d = gate.evaluate(ActionType.UNKNOWN, "pod-4");
            assertFalse(d.syncRequired(), "syncRequired");
            assertEquals("PROJECTION_OK", d.authority(), "authority");
        });

        test("ConsistencyGate: null actionType throws NPE", () -> {
            ConsistencyGate gate = new ConsistencyGate();
            try {
                gate.evaluate(null, "pod-1");
                throw new AssertionError("Expected NullPointerException");
            } catch (NullPointerException e) {
                // expected
            }
        });

        // EventEnvelope tests
        test("EventEnvelope: builder produces valid envelope", () -> {
            EventEnvelope env = validBuilder().build();
            assertNotNull(env.eventId(), "eventId");
            assertEquals("impilo.vito.patient.created.v1", env.eventType(), "eventType");
            assertEquals(1, env.schemaVersion(), "schemaVersion");
            assertNotNull(env.emittedAt(), "emittedAt");
        });

        test("EventEnvelope: schema_version 0 throws", () -> {
            try {
                validBuilder().schemaVersion(0).build();
                throw new AssertionError("Expected SchemaValidationException");
            } catch (SchemaValidationException e) {
                // expected
            }
        });

        test("EventEnvelope: null payload throws", () -> {
            try {
                validBuilder().payload(null).build();
                throw new AssertionError("Expected NullPointerException");
            } catch (NullPointerException e) {
                // expected
            }
        });

        test("EventEnvelope: meta defaults to empty map when null", () -> {
            EventEnvelope env = validBuilder().meta(null).build();
            assertNotNull(env.meta(), "meta");
            assertTrue(env.meta().isEmpty(), "meta empty");
        });

        test("EventEnvelope: payload is immutable", () -> {
            EventEnvelope env = validBuilder().build();
            try {
                env.payload().put("hack", "value");
                throw new AssertionError("Expected UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // expected
            }
        });

        // DeltaPayload tests
        test("DeltaPayload: CREATE is valid", () -> {
            DeltaPayload d = new DeltaPayload("CREATE", null, Map.of("k", "v"), List.of("k"));
            assertEquals("CREATE", d.op(), "op");
        });

        test("DeltaPayload: invalid op throws", () -> {
            try {
                new DeltaPayload("INVALID", null, Map.of(), List.of());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        });

        // InMemoryAuditLedgerClient tests
        test("InMemoryAuditLedgerClient: append and retrieve", () -> {
            InMemoryAuditLedgerClient client = new InMemoryAuditLedgerClient();
            AuditRecord record = sampleAuditRecord();
            client.append(record);
            assertEquals(1, client.size(), "size");
            assertEquals(record, client.getRecords().get(0), "record");
        });

        test("InMemoryAuditLedgerClient: clear resets state", () -> {
            InMemoryAuditLedgerClient client = new InMemoryAuditLedgerClient();
            client.append(sampleAuditRecord());
            client.append(sampleAuditRecord());
            assertEquals(2, client.size(), "before clear");
            client.clear();
            assertEquals(0, client.size(), "after clear");
        });

        test("InMemoryAuditLedgerClient: null record throws", () -> {
            InMemoryAuditLedgerClient client = new InMemoryAuditLedgerClient();
            try {
                client.append(null);
                throw new AssertionError("Expected NullPointerException");
            } catch (NullPointerException e) {
                // expected
            }
        });

        // Summary
        System.out.println("\n=== RESULTS ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total:  " + (passed + failed));

        if (failed > 0) {
            System.out.println("\n*** FAILURES DETECTED ***");
            System.exit(1);
        } else {
            System.out.println("\n*** ALL TESTS PASSED ***");
            System.exit(0);
        }
    }

    // --- helpers ---

    private static EventEnvelope.Builder validBuilder() {
        return EventEnvelope.builder()
                .eventType("impilo.vito.patient.created.v1")
                .schemaVersion(1)
                .correlationId("corr-1")
                .causationId("cause-1")
                .idempotencyKey("idem-1")
                .producer("vito")
                .tenantId("tenant-zw")
                .podId("pod-harare-central")
                .occurredAt(OffsetDateTime.now())
                .subjectType("Patient")
                .subjectId("CPID-12345")
                .payload(Map.of("given_name_hash", "abc123"));
    }

    private static AuditRecord sampleAuditRecord() {
        return new AuditRecord(
                "user-42", "USER", "PATIENT_MERGE", "ALLOW",
                List.of("POLICY_V2_MATCH"), "v2.1",
                "tenant-zw", "pod-harare", "corr-001",
                OffsetDateTime.now(), Map.of("cpid", "CPID-123"));
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

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label + ": expected true but was false");
        }
    }

    private static void assertFalse(boolean condition, String label) {
        if (condition) {
            throw new AssertionError(label + ": expected false but was true");
        }
    }

    private static void assertNotNull(Object obj, String label) {
        if (obj == null) {
            throw new AssertionError(label + ": expected non-null but was null");
        }
    }

    private static void assertContains(String haystack, String needle) {
        if (!haystack.contains(needle)) {
            throw new AssertionError("Expected string containing '" + needle + "' but was: " + haystack);
        }
    }
}
