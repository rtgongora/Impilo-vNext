package zw.gov.mohcc.impilo.shared.biometric;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared verification seam is fail-closed by construction: disabled, unconfigured
 * or unreachable → UNAVAILABLE (never a fabricated MATCH), so any consumer can safely
 * fall back to its existing factor. (HTTP-path mapping is exercised by the ABIS adapter
 * test; here we lock the fail-closed guarantees every consumer relies on.)
 */
class BiometricVerificationClientTest {

    @Test
    @DisplayName("disabled → UNAVAILABLE")
    void disabledFailsClosed() {
        var client = new BiometricVerificationClient(false, "http://abis:8186");
        var d = client.verify("subj-1", "FINGERPRINT", "dGVtcGxhdGU=");
        assertEquals("UNAVAILABLE", d.result());
        assertFalse(d.isMatch());
    }

    @Test
    @DisplayName("missing subject/probe → UNAVAILABLE (no call attempted)")
    void missingInputsFailClosed() {
        var client = new BiometricVerificationClient(true, "http://abis:8186");
        assertEquals("UNAVAILABLE", client.verify(null, "FINGERPRINT", "x").result());
        assertEquals("UNAVAILABLE", client.verify("subj-1", "FINGERPRINT", "").result());
    }

    @Test
    @DisplayName("unreachable ABIS → UNAVAILABLE, never a fabricated match")
    void unreachableFailsClosed() {
        // Points at an unroutable port; the client must fail closed, not throw.
        var client = new BiometricVerificationClient(true, "http://127.0.0.1:1/");
        var d = client.verify("subj-1", "FINGERPRINT", "dGVtcGxhdGU=");
        assertEquals("UNAVAILABLE", d.result());
        assertEquals(0.0, d.confidence());
    }

    @Test
    @DisplayName("Decision.isMatch only for MATCH")
    void isMatchSemantics() {
        assertTrue(new BiometricVerificationClient.Decision("MATCH", 0.9).isMatch());
        assertFalse(new BiometricVerificationClient.Decision("NO_MATCH", 0.1).isMatch());
        assertFalse(BiometricVerificationClient.Decision.unavailable().isMatch());
    }
}
