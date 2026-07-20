package zw.gov.mohcc.impilo.wallet.card;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * B2: the default, dev/simulated {@link NfcCardWriter}. It turns a queued encrypted
 * payload into the exact ISO 7816-4 APDU frame sequence a contactless reader would
 * transmit to the card's secure element — proving the physical-write hand-off end to
 * end — but stops at the wire: no hardware is driven. A real PC/SC reader driver
 * (live APDU transmit + on-SE keygen) is the hardware follow-on and overrides this
 * default by declaring a {@code @Primary} real {@code NfcCardWriter}. This one is a
 * plain {@code @Component} so it always registers as the fallback; {@code @Primary}
 * on the real writer wins when both are present. (A prior {@code @ConditionalOnMissingBean}
 * here was unreliable — that condition is only honoured on auto-configuration classes,
 * not component-scanned beans, so the bean silently failed to register and the service
 * could not start.)
 *
 * <p>Frames built:</p>
 * <ul>
 *   <li>SELECT by AID — {@code 00 A4 04 00 Lc <AID>} (the Impilo card applet).</li>
 *   <li>UPDATE BINARY, chunked — {@code 00 D6 P1 P2 Lc <data>} with P1P2 = the 15-bit
 *       write offset, data ≤ 255 bytes per short-APDU frame.</li>
 * </ul>
 */
@Component
public class SimulatedNfcCardWriter implements NfcCardWriter {

    private static final Logger log = LoggerFactory.getLogger(SimulatedNfcCardWriter.class);

    /** Impilo Health Card applet AID (registered application identifier). */
    private static final byte[] IMPILO_CARD_AID = HexFormat.of().parseHex("A00000061702496D70696C6F");
    private static final int MAX_APDU_DATA = 255; // short-APDU Lc max

    /** Cap the logged frames so a large IPS bundle doesn't flood logs/receipts. */
    private final int maxLoggedFrames;

    public SimulatedNfcCardWriter(
            @Value("${mushe.card-sync.nfc.max-logged-frames:8}") int maxLoggedFrames) {
        this.maxLoggedFrames = maxLoggedFrames;
    }

    @Override
    public WriteReceipt write(UUID cardId, String updateType, byte[] payloadEncrypted) {
        if (payloadEncrypted == null || payloadEncrypted.length == 0) {
            return WriteReceipt.failed("empty payload");
        }
        List<String> apduLog = new ArrayList<>();
        HexFormat hex = HexFormat.of();

        // SELECT AID
        byte[] select = buildSelect(IMPILO_CARD_AID);
        apduLog.add("SELECT " + hex.formatHex(select));

        // UPDATE BINARY, chunked with incrementing offset
        int frames = 1; // the SELECT
        int offset = 0;
        while (offset < payloadEncrypted.length) {
            int len = Math.min(MAX_APDU_DATA, payloadEncrypted.length - offset);
            frames++;
            if (apduLog.size() < maxLoggedFrames) {
                byte[] header = buildUpdateBinaryHeader(offset, len);
                apduLog.add("UPDATE_BINARY off=" + offset + " lc=" + len + " " + hex.formatHex(header) + "…");
            } else if (apduLog.size() == maxLoggedFrames) {
                apduLog.add("… (" + (frames) + " frames total; remainder elided)");
            }
            offset += len;
        }

        String aid = hex.formatHex(IMPILO_CARD_AID);
        log.info("Simulated NFC write cardId={} type={} frames={} bytes={} aid={}",
                cardId, updateType, frames, payloadEncrypted.length, aid);
        return new WriteReceipt("SIMULATED_OK", aid, frames, payloadEncrypted.length, apduLog);
    }

    private static byte[] buildSelect(byte[] aid) {
        byte[] apdu = new byte[5 + aid.length];
        apdu[0] = 0x00;        // CLA
        apdu[1] = (byte) 0xA4; // INS SELECT
        apdu[2] = 0x04;        // P1 select by name (AID)
        apdu[3] = 0x00;        // P2 first/only occurrence
        apdu[4] = (byte) aid.length; // Lc
        System.arraycopy(aid, 0, apdu, 5, aid.length);
        return apdu;
    }

    private static byte[] buildUpdateBinaryHeader(int offset, int lc) {
        // 00 D6 P1 P2 Lc — P1P2 carry the 15-bit offset (ISO 7816-4 §7.2.5).
        return new byte[] {
                0x00,
                (byte) 0xD6,
                (byte) ((offset >> 8) & 0x7F),
                (byte) (offset & 0xFF),
                (byte) lc
        };
    }
}
