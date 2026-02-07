package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Argon2id hashing and verification for VA tokens.
 *
 * <p>The {@link Argon2IdService} produces PHC-format Argon2id hashes stored in
 * the {@code argon2_verifier} column of the {@code provider_tokens} table.
 * Unlike the HMAC lookup hash, the Argon2id verifier uses a random salt per
 * hash, so the same input produces different hashes each time.</p>
 *
 * <p>Uses deliberately low parameters (4096 KiB memory, 1 iteration, 1 lane)
 * for fast test execution. Production uses OWASP-recommended parameters
 * (19456 KiB, 2 iterations, 1 lane).</p>
 *
 * <p>Properties under test:</p>
 * <ul>
 *   <li>PHC output format: {@code $argon2id$v=19$m=...,t=...,p=...${salt}${hash}}</li>
 *   <li>Verification: correct input verifies against its hash</li>
 *   <li>Rejection: wrong input fails verification</li>
 *   <li>Salt randomness: same input produces different hashes</li>
 * </ul>
 */
class Argon2VerifierTest {

    private Argon2IdService argon2Service;

    @BeforeEach
    void setUp() {
        // Use lower params for fast tests: 4 MiB memory, 1 iteration, 1 lane
        argon2Service = new Argon2IdService(4096, 1, 1);
    }

    @Test
    void hash_producesArgon2idFormat() {
        String hash = argon2Service.hash("VA-1234567890");
        assertNotNull(hash);
        assertTrue(hash.startsWith("$argon2id$"),
                "Hash should start with $argon2id$ prefix");
    }

    @Test
    void hash_containsVersionAndParams() {
        String hash = argon2Service.hash("VA-1234567890");
        assertTrue(hash.contains("v=19"),
                "Hash should contain Argon2 version v=19");
        assertTrue(hash.contains("m=4096"),
                "Hash should contain memory parameter m=4096");
        assertTrue(hash.contains("t=1"),
                "Hash should contain iteration parameter t=1");
        assertTrue(hash.contains("p=1"),
                "Hash should contain parallelism parameter p=1");
    }

    @Test
    void hash_hasFiveSegments() {
        String hash = argon2Service.hash("VA-1234567890");
        // Format: $argon2id$v=19$m=4096,t=1,p=1${salt}${hash}
        // Split by $ gives: ["", "argon2id", "v=19", "m=4096,t=1,p=1", "{salt}", "{hash}"]
        String[] segments = hash.split("\\$");
        assertEquals(6, segments.length,
                "PHC format should have 6 segments when split by $");
    }

    @Test
    void verify_correctPassword_returnsTrue() {
        String input = "VA-1234567890";
        String hash = argon2Service.hash(input);
        assertTrue(argon2Service.verify(input, hash),
                "Correct input should verify against its own hash");
    }

    @Test
    void verify_wrongPassword_returnsFalse() {
        String hash = argon2Service.hash("VA-1234567890");
        assertFalse(argon2Service.verify("VA-0000000000", hash),
                "Wrong input should fail verification");
    }

    @Test
    void verify_slightlyDifferentInput_returnsFalse() {
        String hash = argon2Service.hash("VA-1234567890");
        assertFalse(argon2Service.verify("VA-1234567891", hash),
                "Input differing by one character should fail verification");
    }

    @Test
    void sameInput_producesDifferentHashes() {
        // Due to random salt, each call should produce a different hash
        String hash1 = argon2Service.hash("VA-1234567890");
        String hash2 = argon2Service.hash("VA-1234567890");
        assertNotEquals(hash1, hash2,
                "Same input should produce different hashes due to random salt");
    }

    @Test
    void sameInput_bothHashesVerify() {
        String input = "VA-1234567890";
        String hash1 = argon2Service.hash(input);
        String hash2 = argon2Service.hash(input);
        // Both hashes should verify independently
        assertTrue(argon2Service.verify(input, hash1),
                "First hash should verify for the original input");
        assertTrue(argon2Service.verify(input, hash2),
                "Second hash should verify for the original input");
    }

    @Test
    void verify_rejectsMalformedHash() {
        assertFalse(argon2Service.verify("VA-1234567890", "not-a-valid-hash"),
                "Malformed hash string should return false, not throw");
    }

    @Test
    void verify_rejectsEmptyHash() {
        assertFalse(argon2Service.verify("VA-1234567890", ""),
                "Empty hash string should return false");
    }

    @Test
    void defaultConstructor_usesOwaspParameters() {
        Argon2IdService defaultService = new Argon2IdService();
        String hash = defaultService.hash("VA-1234567890");
        assertTrue(hash.contains("m=19456"),
                "Default constructor should use OWASP recommended memory (19456 KiB)");
        assertTrue(hash.contains("t=2"),
                "Default constructor should use OWASP recommended iterations (2)");
        assertTrue(hash.contains("p=1"),
                "Default constructor should use OWASP recommended parallelism (1)");
    }

    @Test
    void verify_withDefaultParams_works() {
        Argon2IdService defaultService = new Argon2IdService();
        String input = "VA-9876543210";
        String hash = defaultService.hash(input);
        assertTrue(defaultService.verify(input, hash),
                "Verification should work with default OWASP parameters");
    }
}
