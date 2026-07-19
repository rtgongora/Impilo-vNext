package zw.gov.mohcc.impilo.wallet.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The simulated NFC writer produces a well-formed ISO 7816-4 APDU frame sequence
 * (SELECT AID + chunked UPDATE BINARY) proving the physical-write hand-off shape.
 */
class SimulatedNfcCardWriterTest {

    private final SimulatedNfcCardWriter writer = new SimulatedNfcCardWriter(8);

    @Test
    @DisplayName("small payload → SELECT + one UPDATE_BINARY frame")
    void smallPayload() {
        byte[] payload = new byte[10];
        var receipt = writer.write(UUID.randomUUID(), "HEALTH_SUMMARY", payload);

        assertTrue(receipt.ok());
        assertEquals("SIMULATED_OK", receipt.status());
        assertEquals(10, receipt.totalBytes());
        assertEquals(2, receipt.frameCount()); // 1 SELECT + 1 UPDATE_BINARY
        assertTrue(receipt.apduLog().get(0).startsWith("SELECT 00a40400"));
        assertFalse(receipt.aid().isBlank());
    }

    @Test
    @DisplayName("payload > 255 bytes → chunked into multiple UPDATE_BINARY frames")
    void chunkedPayload() {
        byte[] payload = new byte[600]; // 255 + 255 + 90 → 3 data frames
        var receipt = writer.write(UUID.randomUUID(), "IPS_BUNDLE", payload);

        assertTrue(receipt.ok());
        assertEquals(600, receipt.totalBytes());
        assertEquals(4, receipt.frameCount()); // 1 SELECT + 3 UPDATE_BINARY
    }

    @Test
    @DisplayName("empty payload → FAILED, never ok")
    void emptyPayload() {
        var receipt = writer.write(UUID.randomUUID(), "HEALTH_SUMMARY", new byte[0]);
        assertFalse(receipt.ok());
        assertEquals("FAILED", receipt.status());
    }
}
