package zw.gov.mohcc.impilo.security.secrets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequiredSecretTest {

    @Test
    void acceptsStrongSecret() {
        String value = "a-strong-secret-of-at-least-32-bytes!!";
        assertEquals(value, RequiredSecret.require("demo.secret", value, "demo purpose"));
        assertTrue(RequiredSecret.isUsable(value));
    }

    @Test
    void rejectsBlankShortAndPlaceholder() {
        assertThrows(IllegalStateException.class,
                () -> RequiredSecret.require("demo.secret", "", "demo"));
        assertThrows(IllegalStateException.class,
                () -> RequiredSecret.require("demo.secret", null, "demo"));
        assertThrows(IllegalStateException.class,
                () -> RequiredSecret.require("demo.secret", "too-short", "demo"));
        assertThrows(IllegalStateException.class,
                () -> RequiredSecret.require("demo.secret",
                        "vito-qr-signing-dev-seed-change-me-32b", "demo"));
        assertFalse(RequiredSecret.isUsable(""));
        assertFalse(RequiredSecret.isUsable("change-me-padded-to-thirty-two-chars!!"));
    }

    @Test
    void exceptionNeverEchoesTheSecret() {
        String secret = "super-secret-value-that-must-not-leak!!";
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> RequiredSecret.require("demo.secret", "x", "demo"));
        assertFalse(ex.getMessage().contains(secret));
        assertTrue(ex.getMessage().contains("demo.secret"));
    }
}
