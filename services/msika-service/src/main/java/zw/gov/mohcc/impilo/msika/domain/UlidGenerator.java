package zw.gov.mohcc.impilo.msika.domain;

import java.security.SecureRandom;
import java.time.Instant;

public final class UlidGenerator {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private UlidGenerator() {}

    public static String generate() {
        long timestamp = Instant.now().toEpochMilli();
        StringBuilder sb = new StringBuilder(26);
        // Encode timestamp (10 chars, 48 bits)
        for (int i = 9; i >= 0; i--) {
            sb.insert(0, ENCODING[(int) (timestamp & 0x1F)]);
            timestamp >>>= 5;
        }
        // Encode randomness (16 chars, 80 bits)
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);
        for (int i = 0; i < 10; i++) {
            int val = randomBytes[i] & 0xFF;
            sb.append(ENCODING[val >>> 3]);
            if (i < 9) {
                int combined = ((val & 0x07) << 2) | ((randomBytes[i + 1] & 0xFF) >>> 6);
                sb.append(ENCODING[combined & 0x1F]);
                randomBytes[i + 1] = (byte) (randomBytes[i + 1] & 0x3F);
            } else {
                sb.append(ENCODING[val & 0x07]);
            }
        }
        return sb.substring(0, 26);
    }
}
