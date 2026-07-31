package zw.gov.mohcc.impilo.vito.core.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Identity Contract §6 / C1 — the Impilo ID is encrypted at rest and never
 * stored in plaintext.
 */
@DisplayName("ImpiloIdCipher (AES-256-GCM)")
class ImpiloIdCipherTest {

    private final ImpiloIdCipher cipher =
            new ImpiloIdCipher("a-strong-test-encryption-key-32bytes+");

    @Test
    @DisplayName("round-trips the plaintext Impilo ID")
    void roundTrip() {
        String id = "1234567897";
        String enc = cipher.encrypt(id);
        assertNotEquals(id, enc, "ciphertext must differ from plaintext");
        assertTrue(enc.startsWith("v1:"), "envelope must carry a key version");
        assertFalse(enc.contains(id), "plaintext must not appear in the envelope");
        assertEquals(id, cipher.decrypt(enc));
    }

    @Test
    @DisplayName("encryption is randomised — same input yields different ciphertext")
    void randomisedPerWrite() {
        String id = "9876543213";
        assertNotEquals(cipher.encrypt(id), cipher.encrypt(id),
                "GCM IV must be fresh per write (hence not value-queryable)");
    }

    @Test
    @DisplayName("null passes through both directions")
    void nullPassThrough() {
        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));
    }

    @Test
    @DisplayName("tolerates legacy plaintext values (pre-migration / seeds)")
    void legacyPlaintextTolerated() {
        // A 10-char plaintext value with no ':' envelope decrypts to itself.
        assertEquals("1234567897", cipher.decrypt("1234567897"));
    }

    @Test
    @DisplayName("wrong key fails to decrypt (integrity)")
    void wrongKeyFails() {
        String enc = cipher.encrypt("5550192737");
        ImpiloIdCipher other = new ImpiloIdCipher("a-DIFFERENT-strong-key-of-32-bytes++");
        assertThrows(IllegalStateException.class, () -> other.decrypt(enc));
    }

    @Test
    @DisplayName("blank or placeholder key → refuse to construct")
    void failsClosedOnBlankOrWeakKey() {
        assertThrows(IllegalStateException.class, () -> new ImpiloIdCipher(""));
        assertThrows(IllegalStateException.class, () -> new ImpiloIdCipher("too-short"));
        assertThrows(IllegalStateException.class,
                () -> new ImpiloIdCipher("vito-impilo-id-dev-key-change-me-32b!!"));
    }
}
