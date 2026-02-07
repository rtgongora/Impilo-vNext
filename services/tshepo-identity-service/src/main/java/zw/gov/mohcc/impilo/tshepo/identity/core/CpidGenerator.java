package zw.gov.mohcc.impilo.tshepo.identity.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.identity.config.IdentityProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Generates CPIDs (Clinical Patient Identifiers) using two strategies:
 *
 * <h3>Canonical CPID (deterministic — UUID v5)</h3>
 * <p>SHA-1 namespace UUID + tenantId + healthId yields a reproducible UUID.
 * This is collision-safe because the (tenantId, healthId) pair is unique
 * within the id_mapping table.</p>
 *
 * <h3>Provisional O-CPID (random)</h3>
 * <p>A random UUID v4 generated for offline use. The O-CPID is later reconciled
 * to a canonical CPID once the facility reconnects.</p>
 */
@Component
public class CpidGenerator {

    private static final Logger log = LoggerFactory.getLogger(CpidGenerator.class);

    private final UUID namespace;

    public CpidGenerator(IdentityProperties properties) {
        this.namespace = UUID.fromString(properties.cpidNamespace());
        log.info("CpidGenerator initialised with namespace={}", this.namespace);
    }

    /**
     * Generate a deterministic CPID (UUID v5) from tenantId + healthId.
     *
     * <p>UUID v5 algorithm: SHA-1(namespace bytes + name bytes), then set version=5
     * and variant=RFC4122.</p>
     */
    public UUID generateCpid(UUID tenantId, UUID healthId) {
        String name = tenantId.toString() + ":" + healthId.toString();
        return uuidV5(namespace, name);
    }

    /**
     * Generate a random provisional O-CPID (UUID v4).
     *
     * <p>The "O-" prefix convention is tracked at the database level in the
     * provisional_cpid table, not embedded in the UUID itself.</p>
     */
    public UUID generateProvisionalCpid() {
        return UUID.randomUUID();
    }

    /**
     * UUID v5 implementation per RFC 4122 Section 4.3.
     *
     * <p>Computes SHA-1 over the namespace UUID bytes (big-endian) concatenated
     * with the name bytes (UTF-8), then sets the version to 5 and variant to
     * RFC 4122 (10xx).</p>
     */
    private static UUID uuidV5(UUID namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

            // Namespace UUID as 16 big-endian bytes
            long msb = namespace.getMostSignificantBits();
            long lsb = namespace.getLeastSignificantBits();
            byte[] nsBytes = new byte[16];
            for (int i = 7; i >= 0; i--) {
                nsBytes[i] = (byte) (msb & 0xff);
                msb >>= 8;
            }
            for (int i = 15; i >= 8; i--) {
                nsBytes[i] = (byte) (lsb & 0xff);
                lsb >>= 8;
            }

            sha1.update(nsBytes);
            sha1.update(name.getBytes(StandardCharsets.UTF_8));

            byte[] hash = sha1.digest();

            // Set version to 5 (bits 4-7 of byte 6)
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            // Set variant to RFC 4122 (bits 6-7 of byte 8 = 10)
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);

            // Construct UUID from first 16 bytes of SHA-1 hash
            long resultMsb = 0;
            long resultLsb = 0;
            for (int i = 0; i < 8; i++) {
                resultMsb = (resultMsb << 8) | (hash[i] & 0xff);
            }
            for (int i = 8; i < 16; i++) {
                resultLsb = (resultLsb << 8) | (hash[i] & 0xff);
            }

            return new UUID(resultMsb, resultLsb);
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is guaranteed to be available in all Java implementations
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
