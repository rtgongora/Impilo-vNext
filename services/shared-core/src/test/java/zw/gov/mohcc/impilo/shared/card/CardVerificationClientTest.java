package zw.gov.mohcc.impilo.shared.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The card-resolve seam is fail-closed: disabled, empty, or an unreachable VITO
 * yields {@code unresolved()} (never a fabricated identity) and never throws.
 */
class CardVerificationClientTest {

    @Test
    @DisplayName("disabled → unresolved")
    void disabledUnresolved() {
        var client = new CardVerificationClient(false, "http://vito:8082");
        assertFalse(client.resolveByCardNumber("CARD-1").resolved());
    }

    @Test
    @DisplayName("no presentation → unresolved")
    void emptyUnresolved() {
        var client = new CardVerificationClient(true, "http://vito:8082");
        assertFalse(client.resolve(null, null, null).resolved());
        assertFalse(client.resolve("", "", "").resolved());
    }

    @Test
    @DisplayName("unreachable VITO → unresolved, never throws")
    void unreachableUnresolved() {
        var client = new CardVerificationClient(true, "http://127.0.0.1:1/");
        var res = client.resolveByCardNumber("CARD-1");
        assertFalse(res.resolved());
        assertNull(res.healthId());
    }

    @Test
    @DisplayName("Resolution helpers")
    void resolutionHelpers() {
        assertFalse(CardVerificationClient.Resolution.unresolved().isActive());
        var active = new CardVerificationClient.Resolution(
                true, "h", "h", "CARD-1", "ACTIVE", "PHYSICAL", "CARD_NUMBER");
        assertTrue(active.isActive());
        var revoked = new CardVerificationClient.Resolution(
                true, "h", "h", "CARD-1", "REVOKED", "PHYSICAL", "CARD_NUMBER");
        assertFalse(revoked.isActive());
    }
}
