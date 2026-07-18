package zw.gov.mohcc.impilo.tshepo.identity.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CpidGenerator}.
 *
 * <p>Per the Identity Contract §7, CPIDs are independent random UUID v4 values —
 * these tests assert version 4 and <b>non</b>-determinism (the inverse of the
 * retired UUID v5 derivation, which made CPIDs computable from the Health ID).</p>
 */
@DisplayName("CpidGenerator")
class CpidGeneratorTest {

    private CpidGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CpidGenerator();
    }

    // ── Canonical CPID (random UUID v4) ─────────────────────────────────────

    @Nested
    @DisplayName("generateCpid (independent random UUID v4)")
    class GenerateCpid {

        @Test
        @DisplayName("returns a non-null, round-trippable UUID")
        void generate_returnsValidUuid() {
            UUID cpid = generator.generateCpid();

            assertNotNull(cpid, "CPID must not be null");
            assertEquals(cpid, UUID.fromString(cpid.toString()),
                    "CPID must round-trip through toString/fromString");
        }

        @Test
        @DisplayName("returns a UUID with version 4 (random) — never a derived v5")
        void generate_returnsUuidV4() {
            UUID cpid = generator.generateCpid();

            assertEquals(4, cpid.version(),
                    "Canonical CPID must be UUID version 4 (random); a v5 value would "
                    + "indicate derivation from the Health ID, which the Identity "
                    + "Contract forbids");
        }

        @Test
        @DisplayName("returns a UUID with RFC 4122 variant")
        void generate_returnsRfc4122Variant() {
            UUID cpid = generator.generateCpid();

            assertEquals(2, cpid.variant(),
                    "CPID must use the RFC 4122 variant (variant bits = 10)");
        }

        @Test
        @DisplayName("is non-deterministic: successive calls return distinct values")
        void generate_successiveCalls_returnDistinct() {
            UUID cpid1 = generator.generateCpid();
            UUID cpid2 = generator.generateCpid();
            UUID cpid3 = generator.generateCpid();

            assertNotEquals(cpid1, cpid2,
                    "CPID generation must be non-deterministic — equal values would "
                    + "mean the CPID is computable outside the id_mapping table");
            assertNotEquals(cpid2, cpid3);
            assertNotEquals(cpid1, cpid3);
        }

        @Test
        @DisplayName("batch of generated CPIDs are all unique")
        void generate_batch_allUnique() {
            Set<UUID> cpids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                cpids.add(generator.generateCpid());
            }
            assertEquals(1000, cpids.size(), "1000 generated CPIDs must all be unique");
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
