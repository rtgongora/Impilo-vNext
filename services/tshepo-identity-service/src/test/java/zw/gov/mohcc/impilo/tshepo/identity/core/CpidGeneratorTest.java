package zw.gov.mohcc.impilo.tshepo.identity.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.identity.config.IdentityProperties;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CpidGenerator}.
 *
 * <p>Validates deterministic UUID v5 generation from namespace + tenantId + healthId,
 * tenant isolation, and provisional O-CPID randomness.</p>
 */
@DisplayName("CpidGenerator")
class CpidGeneratorTest {

    /** Well-known DNS namespace UUID used for testing. */
    private static final String TEST_NAMESPACE = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

    private CpidGenerator generator;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = new IdentityProperties(
                TEST_NAMESPACE,
                300,
                null,
                null,
                null
        );
        generator = new CpidGenerator(properties);
    }

    // ── Fixture helpers ────────────────────────────────────────────────────

    private static UUID tenantA() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private static UUID tenantB() {
        return UUID.fromString("00000000-0000-0000-0000-000000000002");
    }

    private static UUID healthId1() {
        return UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    private static UUID healthId2() {
        return UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    }

    // ── Deterministic (UUID v5) CPID tests ─────────────────────────────────

    @Nested
    @DisplayName("generateCpid (deterministic UUID v5)")
    class GenerateCpid {

        @Test
        @DisplayName("same inputs produce the same CPID every time")
        void generate_withSameInputs_returnsSameCpid() {
            UUID cpid1 = generator.generateCpid(tenantA(), healthId1());
            UUID cpid2 = generator.generateCpid(tenantA(), healthId1());
            UUID cpid3 = generator.generateCpid(tenantA(), healthId1());

            assertEquals(cpid1, cpid2, "CPID must be deterministic across calls");
            assertEquals(cpid2, cpid3, "CPID must be deterministic across many calls");
        }

        @Test
        @DisplayName("different tenantId produces a different CPID (tenant isolation)")
        void generate_withDifferentTenantId_returnsDifferentCpid() {
            UUID cpidTenantA = generator.generateCpid(tenantA(), healthId1());
            UUID cpidTenantB = generator.generateCpid(tenantB(), healthId1());

            assertNotEquals(cpidTenantA, cpidTenantB,
                    "CPIDs must differ across tenants for the same healthId");
        }

        @Test
        @DisplayName("different healthId produces a different CPID")
        void generate_withDifferentHealthId_returnsDifferentCpid() {
            UUID cpidHealth1 = generator.generateCpid(tenantA(), healthId1());
            UUID cpidHealth2 = generator.generateCpid(tenantA(), healthId2());

            assertNotEquals(cpidHealth1, cpidHealth2,
                    "CPIDs must differ for different healthIds within the same tenant");
        }

        @Test
        @DisplayName("returns a valid UUID (non-null, parseable)")
        void generate_returnsValidUuid() {
            UUID cpid = generator.generateCpid(tenantA(), healthId1());

            assertNotNull(cpid, "CPID must not be null");
            // Verify it round-trips through UUID.fromString
            String cpidStr = cpid.toString();
            UUID reparsed = UUID.fromString(cpidStr);
            assertEquals(cpid, reparsed, "CPID must round-trip through toString/fromString");
        }

        @Test
        @DisplayName("returns a UUID with version 5")
        void generate_returnsUuidV5() {
            UUID cpid = generator.generateCpid(tenantA(), healthId1());

            assertEquals(5, cpid.version(),
                    "Canonical CPID must be UUID version 5");
        }

        @Test
        @DisplayName("returns a UUID with RFC 4122 variant")
        void generate_returnsRfc4122Variant() {
            UUID cpid = generator.generateCpid(tenantA(), healthId1());

            assertEquals(2, cpid.variant(),
                    "CPID must use the RFC 4122 variant (variant bits = 10)");
        }

        @Test
        @DisplayName("deterministic across separate generator instances with same namespace")
        void generate_acrossInstances_returnsSameCpid() {
            IdentityProperties sameProps = new IdentityProperties(
                    TEST_NAMESPACE, 300, null, null, null);
            CpidGenerator anotherGenerator = new CpidGenerator(sameProps);

            UUID cpid1 = generator.generateCpid(tenantA(), healthId1());
            UUID cpid2 = anotherGenerator.generateCpid(tenantA(), healthId1());

            assertEquals(cpid1, cpid2,
                    "Two generators with the same namespace must produce identical CPIDs");
        }

        @Test
        @DisplayName("different namespace produces a different CPID")
        void generate_withDifferentNamespace_returnsDifferentCpid() {
            IdentityProperties altProps = new IdentityProperties(
                    "a1a2a3a4-b1b2-c1c2-d1d2-e1e2e3e4e5e6",
                    300, null, null, null);
            CpidGenerator altGenerator = new CpidGenerator(altProps);

            UUID cpidDefault = generator.generateCpid(tenantA(), healthId1());
            UUID cpidAlt = altGenerator.generateCpid(tenantA(), healthId1());

            assertNotEquals(cpidDefault, cpidAlt,
                    "Different namespaces must produce different CPIDs");
        }

        @Test
        @DisplayName("generates unique CPIDs for a batch of distinct inputs")
        void generate_batchDistinctInputs_allUnique() {
            Set<UUID> cpids = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                UUID tenant = UUID.randomUUID();
                UUID health = UUID.randomUUID();
                cpids.add(generator.generateCpid(tenant, health));
            }
            assertEquals(100, cpids.size(),
                    "100 distinct (tenantId, healthId) pairs must yield 100 unique CPIDs");
        }
    }

    // ── Provisional (UUID v4) O-CPID tests ─────────────────────────────────

    @Nested
    @DisplayName("generateProvisionalCpid (random UUID v4)")
    class GenerateProvisionalCpid {

        @Test
        @DisplayName("returns a non-null UUID")
        void generateProvisional_returnsNonNull() {
            UUID oCpid = generator.generateProvisionalCpid();
            assertNotNull(oCpid, "Provisional O-CPID must not be null");
        }

        @Test
        @DisplayName("returns a UUID with version 4 (random)")
        void generateProvisional_returnsUuidV4() {
            UUID oCpid = generator.generateProvisionalCpid();
            assertEquals(4, oCpid.version(),
                    "Provisional O-CPID must be UUID version 4");
        }

        @Test
        @DisplayName("successive calls return distinct values")
        void generateProvisional_successiveCalls_returnDistinct() {
            UUID oCpid1 = generator.generateProvisionalCpid();
            UUID oCpid2 = generator.generateProvisionalCpid();
            UUID oCpid3 = generator.generateProvisionalCpid();

            assertNotEquals(oCpid1, oCpid2);
            assertNotEquals(oCpid2, oCpid3);
            assertNotEquals(oCpid1, oCpid3);
        }

        @Test
        @DisplayName("batch of provisional CPIDs are all unique")
        void generateProvisional_batch_allUnique() {
            Set<UUID> oCpids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                oCpids.add(generator.generateProvisionalCpid());
            }
            assertEquals(1000, oCpids.size(),
                    "1000 provisional O-CPIDs must all be unique");
        }
    }
}
