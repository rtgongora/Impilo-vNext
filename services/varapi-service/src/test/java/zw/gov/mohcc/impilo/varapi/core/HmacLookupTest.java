package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the HMAC-SHA256 lookup hash pattern used for VA tokens.
 *
 * <p>The {@link HmacService} provides deterministic, non-reversible hashes
 * that serve as indexed lookup keys in the {@code provider_tokens} table.
 * This allows O(1) token retrieval without storing plaintext VA tokens.</p>
 *
 * <p>Properties under test:</p>
 * <ul>
 *   <li>Determinism: same (pepper, input) always produces the same hash</li>
 *   <li>Collision resistance: different inputs produce different hashes</li>
 *   <li>Verification: {@code verifyLookupHash} matches computed values</li>
 *   <li>Output format: hex-encoded, lowercase, 64 characters (SHA-256)</li>
 * </ul>
 */
class HmacLookupTest {

    private static final String TEST_PEPPER = "test-pepper-for-unit-tests";

    private HmacService hmacService;

    @BeforeEach
    void setUp() {
        hmacService = new HmacService(TEST_PEPPER);
    }

    @Test
    void sameInput_producesSameHash() {
        String hash1 = hmacService.computeLookupHash("VA-1234567890");
        String hash2 = hmacService.computeLookupHash("VA-1234567890");
        assertEquals(hash1, hash2,
                "Same input with same pepper must produce identical hashes");
    }

    @Test
    void differentInput_producesDifferentHash() {
        String hash1 = hmacService.computeLookupHash("VA-1234567890");
        String hash2 = hmacService.computeLookupHash("VA-0987654321");
        assertNotEquals(hash1, hash2,
                "Different inputs should produce different hashes");
    }

    @Test
    void differentPepper_producesDifferentHash() {
        HmacService otherService = new HmacService("different-pepper-value");
        String hash1 = hmacService.computeLookupHash("VA-5551234567");
        String hash2 = otherService.computeLookupHash("VA-5551234567");
        assertNotEquals(hash1, hash2,
                "Same input with different pepper should produce different hashes");
    }

    @Test
    void verify_matchesComputed() {
        String input = "VA-5551234567";
        String hash = hmacService.computeLookupHash(input);
        assertTrue(hmacService.verifyLookupHash(input, hash),
                "verifyLookupHash should return true for a matching input and hash");
    }

    @Test
    void verify_rejectsMismatch() {
        String hash = hmacService.computeLookupHash("VA-2222222222");
        assertFalse(hmacService.verifyLookupHash("VA-1111111111", hash),
                "verifyLookupHash should return false for mismatched input");
    }

    @Test
    void hashOutput_isHexEncoded() {
        String hash = hmacService.computeLookupHash("VA-1234567890");
        assertNotNull(hash);
        assertTrue(hash.matches("[a-f0-9]+"),
                "Hash output should be lowercase hex characters only");
    }

    @Test
    void hashOutput_isSha256Length() {
        String hash = hmacService.computeLookupHash("VA-1234567890");
        assertEquals(64, hash.length(),
                "HMAC-SHA256 output should be 64 hex characters (32 bytes)");
    }

    @Test
    void constructor_rejectsNullPepper() {
        assertThrows(IllegalArgumentException.class,
                () -> new HmacService(null),
                "Null pepper should throw IllegalArgumentException");
    }

    @Test
    void constructor_rejectsBlankPepper() {
        assertThrows(IllegalArgumentException.class,
                () -> new HmacService("   "),
                "Blank pepper should throw IllegalArgumentException");
    }

    @Test
    void computeLookupHash_rejectsNullInput() {
        assertThrows(IllegalArgumentException.class,
                () -> hmacService.computeLookupHash(null),
                "Null input should throw IllegalArgumentException");
    }

    @Test
    void computeLookupHash_rejectsBlankInput() {
        assertThrows(IllegalArgumentException.class,
                () -> hmacService.computeLookupHash("  "),
                "Blank input should throw IllegalArgumentException");
    }

    @Test
    void singleCharacterDifference_producesCompletelyDifferentHash() {
        String hash1 = hmacService.computeLookupHash("VA-1234567890");
        String hash2 = hmacService.computeLookupHash("VA-1234567891");
        assertNotEquals(hash1, hash2);
        // Verify avalanche — most characters should differ
        int diffCount = 0;
        for (int i = 0; i < hash1.length(); i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) diffCount++;
        }
        assertTrue(diffCount > hash1.length() / 4,
                "HMAC should exhibit avalanche effect: expected >16 char diffs, got " + diffCount);
    }
}
