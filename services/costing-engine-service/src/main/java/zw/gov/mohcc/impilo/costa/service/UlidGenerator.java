package zw.gov.mohcc.impilo.costa.service;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Generates ULID (Universally Unique Lexicographically Sortable Identifier) strings.
 * 26-character Crockford Base32 encoding: 10 chars timestamp + 16 chars randomness.
 */
public final class UlidGenerator {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private UlidGenerator() {}

    public static String generate() {
        long timestamp = Instant.now().toEpochMilli();
        StringBuilder sb = new StringBuilder(26);

        // Encode timestamp (10 chars)
        for (int i = 9; i >= 0; i--) {
            sb.insert(0, ENCODING[(int) (timestamp & 0x1F)]);
            timestamp >>>= 5;
        }

        // Encode randomness (16 chars)
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);
        for (int i = 0; i < 16; i++) {
            int idx = i < 10 ? (randomBytes[i] & 0x1F) : (RANDOM.nextInt(32));
            sb.append(ENCODING[idx]);
        }

        return sb.toString();
    }
}
