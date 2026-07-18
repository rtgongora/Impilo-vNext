package zw.gov.mohcc.impilo.vito.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Identity Journey Doctrine §2 — deterministic, normalised, non-reversible
 * contact lookup keys for private matching.
 */
@DisplayName("ContactHasher")
class ContactHasherTest {

    private final ContactHasher hasher = new ContactHasher(new HmacService("a-test-pepper-value"));

    @Test
    @DisplayName("phone hashing is deterministic and normalisation-insensitive")
    void phoneDeterministicAndNormalised() {
        String a = hasher.hashPhone("+263 77 123 4567");
        String b = hasher.hashPhone("+263771234567");
        assertEquals(a, b, "spacing must not change the hash");
        assertNotNull(a);
        assertFalse(a.contains("263"), "the hash must not reveal the number");
    }

    @Test
    @DisplayName("email hashing is case/whitespace-insensitive")
    void emailNormalised() {
        assertEquals(hasher.hashEmail("A@B.com"), hasher.hashEmail("  a@b.com "));
    }

    @Test
    @DisplayName("different contacts produce different hashes")
    void distinctInputsDistinctHashes() {
        assertNotEquals(hasher.hashPhone("+263771234567"), hasher.hashPhone("+263779999999"));
        assertNotEquals(hasher.hashEmail("a@b.com"), hasher.hashEmail("c@d.com"));
    }

    @Test
    @DisplayName("null/blank/kind routing")
    void nullBlankAndKind() {
        assertNull(hasher.hashPhone(null));
        assertNull(hasher.hashPhone("   "));
        assertNull(hasher.hashEmail(null));
        assertNull(hasher.hash("unknown", "x"));
        assertEquals(hasher.hashPhone("+263771234567"), hasher.hash("PHONE", "+263771234567"));
        assertEquals(hasher.hashEmail("a@b.com"), hasher.hash("email", "a@b.com"));
    }
}
