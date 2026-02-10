package zw.gov.mohcc.impilo.vito.core.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Idempotency enforcement service for v1.1 POST/PUT/PATCH endpoints.
 *
 * Request hash = SHA-256(METHOD + PATH + raw request body bytes), hex-encoded.
 *
 * Behavior:
 *   - same key + same hash  => replay stored response (status + body)
 *   - same key + different hash => IDENTITY_CONFLICT (409)
 *   - new key => proceed, then store response
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository repository;

    public IdempotencyService(IdempotencyRepository repository) {
        this.repository = repository;
    }

    /**
     * Compute SHA-256 hash of (method + path + body) as hex string.
     */
    public String computeHash(String method, String path, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            if (body != null && body.length > 0) {
                digest.update(body);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JCA spec
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Look up an existing record for this idempotency key.
     */
    public Optional<IdempotencyRecord> find(String tenantId, String podId, String idempotencyKey) {
        return repository.find(tenantId, podId, idempotencyKey);
    }

    /**
     * Store a new idempotency record after successful request processing.
     */
    public void store(String tenantId, String podId, String idempotencyKey,
                      String requestHash, int responseStatus, String responseBody) {
        IdempotencyRecord record = new IdempotencyRecord(
                tenantId, podId, idempotencyKey, requestHash,
                responseStatus, responseBody,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(24) // 24h TTL
        );
        try {
            repository.insert(record);
        } catch (Exception e) {
            // Race condition: another thread may have inserted first. Log and continue.
            log.warn("Failed to store idempotency record (key={}): {}", idempotencyKey, e.getMessage());
        }
    }
}
