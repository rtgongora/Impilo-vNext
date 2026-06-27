package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * RFC 6238 TOTP primitives shared by the step-up verifier and the enrolment flow:
 * code generation (HMAC-SHA1), constant-time verification with a ±1 step window,
 * RFC 4648 base32 secret encoding (for {@code otpauth://} URIs), and secret generation.
 */
public final class TotpCodes {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpCodes() {
    }

    /** Generate the {@code digits}-length TOTP code for a counter (time-step). */
    public static String generate(byte[] secret, long counter, int digits) {
        try {
            byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] h = mac.doFinal(msg);
            int offset = h[h.length - 1] & 0xF;
            int binary = ((h[offset] & 0x7f) << 24)
                    | ((h[offset + 1] & 0xff) << 16)
                    | ((h[offset + 2] & 0xff) << 8)
                    | (h[offset + 3] & 0xff);
            int mod = (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", binary % mod);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    /** Verify a code against the current time-step ±1 (clock skew), constant-time. */
    public static boolean verify(byte[] secret, String code, int digits, int periodSeconds) {
        if (code == null || !code.matches("\\d{" + digits + "}")) {
            return false;
        }
        long step = Instant.now().getEpochSecond() / periodSeconds;
        for (long w = -1; w <= 1; w++) {
            if (constantTimeEquals(generate(secret, step + w, digits), code)) {
                return true;
            }
        }
        return false;
    }

    /** RFC 4648 base32 (no padding) — the {@code otpauth://} secret encoding. */
    public static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                sb.append(BASE32_ALPHABET.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32_ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    /** Generate a cryptographically random secret of {@code n} bytes (160-bit default for SHA-1). */
    public static byte[] randomSecret(int n) {
        byte[] secret = new byte[n];
        RANDOM.nextBytes(secret);
        return secret;
    }

    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
