package zw.gov.mohcc.impilo.mushex.service;

import java.security.SecureRandom;
import java.time.Instant;

public final class UlidGenerator {
    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private UlidGenerator() {}

    public static String generate() {
        long timestamp = Instant.now().toEpochMilli();
        StringBuilder sb = new StringBuilder(26);
        for (int i = 9; i >= 0; i--) {
            sb.insert(0, ENCODING[(int) (timestamp & 0x1F)]);
            timestamp >>>= 5;
        }
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);
        for (int i = 0; i < 16; i++) {
            int idx = i < 10 ? (randomBytes[i] & 0x1F) : (RANDOM.nextInt(32));
            sb.append(ENCODING[idx]);
        }
        return sb.toString();
    }
}
