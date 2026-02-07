package zw.gov.mohcc.impilo.shared.crypto;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Argon2id hashing service for the Impilo Identity Alias Pattern.
 *
 * Used by VITO to store identity verifiers without keeping plaintext.
 * Parameters follow OWASP 2024 recommendations for Argon2id:
 *   - Memory: 19 MiB minimum (configurable up to 64 MiB)
 *   - Iterations: 2
 *   - Parallelism: 1
 *
 * Output format (PHC string): $argon2id$v=19$m={memory},t={iterations},p={parallelism}${salt}${hash}
 *
 * Thread-safe: each call creates its own generator instance.
 */
public class Argon2IdService {

    private static final int HASH_LENGTH = 32;
    private static final int SALT_LENGTH = 16;

    private final int memoryCostKb;
    private final int iterations;
    private final int parallelism;
    private final SecureRandom secureRandom;

    /**
     * Create with OWASP 2024 minimum parameters.
     */
    public Argon2IdService() {
        this(19456, 2, 1);
    }

    /**
     * Create with custom parameters.
     *
     * @param memoryCostKb memory in KiB (OWASP minimum: 19456 = 19 MiB)
     * @param iterations   time cost (OWASP minimum: 2)
     * @param parallelism  lanes (OWASP minimum: 1)
     */
    public Argon2IdService(int memoryCostKb, int iterations, int parallelism) {
        this.memoryCostKb = memoryCostKb;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Hash a normalized identity value using Argon2id.
     *
     * @param input the normalized identity value (e.g., normalized impilo_id)
     * @return PHC-format string: $argon2id$v=19$m=...,t=...,p=...${base64_salt}${base64_hash}
     */
    public String hash(String input) {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(memoryCostKb)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] hash = new byte[HASH_LENGTH];
        generator.generateBytes(input.toCharArray(), hash);

        String saltB64 = Base64.getEncoder().withoutPadding().encodeToString(salt);
        String hashB64 = Base64.getEncoder().withoutPadding().encodeToString(hash);

        return String.format("$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s",
                memoryCostKb, iterations, parallelism, saltB64, hashB64);
    }

    /**
     * Verify a normalized identity value against a stored PHC-format hash.
     *
     * @param input  the normalized identity value to verify
     * @param stored the stored PHC-format hash from {@link #hash(String)}
     * @return true if the input matches the stored hash
     */
    public boolean verify(String input, String stored) {
        PhcParts parts = PhcParts.parse(stored);
        if (parts == null) {
            return false;
        }

        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(parts.salt)
                .withMemoryAsKB(parts.memory)
                .withIterations(parts.iterations)
                .withParallelism(parts.parallelism)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] computedHash = new byte[parts.hash.length];
        generator.generateBytes(input.toCharArray(), computedHash);

        return constantTimeEquals(computedHash, parts.hash);
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * Parses a PHC-format Argon2id string into its component parts.
     */
    private record PhcParts(int memory, int iterations, int parallelism, byte[] salt, byte[] hash) {
        static PhcParts parse(String phc) {
            try {
                // Format: $argon2id$v=19$m=19456,t=2,p=1${salt}${hash}
                String[] segments = phc.split("\\$");
                if (segments.length != 6 || !segments[1].equals("argon2id")) {
                    return null;
                }

                String[] paramParts = segments[3].split(",");
                int m = Integer.parseInt(paramParts[0].substring(2));
                int t = Integer.parseInt(paramParts[1].substring(2));
                int p = Integer.parseInt(paramParts[2].substring(2));

                byte[] salt = Base64.getDecoder().decode(padBase64(segments[4]));
                byte[] hash = Base64.getDecoder().decode(padBase64(segments[5]));

                return new PhcParts(m, t, p, salt, hash);
            } catch (Exception e) {
                return null;
            }
        }

        private static String padBase64(String unpadded) {
            int remainder = unpadded.length() % 4;
            if (remainder == 0) return unpadded;
            return unpadded + "=".repeat(4 - remainder);
        }
    }
}
