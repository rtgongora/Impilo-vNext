package zw.gov.mohcc.impilo.wallet.card;

import java.util.List;
import java.util.UUID;

/**
 * B2: the seam that writes a queued card update onto the physical smart card.
 *
 * <p>The pipeline is: {@code card_update_queue} (PENDING) → {@link CardUpdateQueueConsumer}
 * drains it → THIS writer transmits the encrypted payload to the card's secure element
 * as ISO 7816-4 APDU frames. The {@link SimulatedNfcCardWriter} default produces the
 * exact APDU frame sequence (SELECT AID + chunked UPDATE BINARY) proving the hand-off
 * shape without hardware; a driver-backed writer for a real PC/SC contactless reader
 * (on-SE keygen + live APDU transmit) is the hardware follow-on that replaces it behind
 * this same interface.</p>
 */
public interface NfcCardWriter {

    /**
     * Write an encrypted card-update payload to the card.
     *
     * @param cardId            the target card
     * @param updateType        HEALTH_SUMMARY | IPS_BUNDLE
     * @param payloadEncrypted  the AES-256-GCM ciphertext to write (IV-prefixed)
     * @return a receipt describing the APDU exchange (never null; carries the status)
     */
    WriteReceipt write(UUID cardId, String updateType, byte[] payloadEncrypted);

    /** Result of an APDU write exchange. {@code SIMULATED_OK} = frames built, no hardware transmit. */
    record WriteReceipt(String status, String aid, int frameCount, int totalBytes, List<String> apduLog) {
        public boolean ok() {
            return "SIMULATED_OK".equals(status) || "OK".equals(status);
        }

        public static WriteReceipt failed(String reason) {
            return new WriteReceipt("FAILED", null, 0, 0, List.of(reason));
        }
    }
}
