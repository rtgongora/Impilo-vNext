package zw.gov.mohcc.impilo.companion.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Idempotency enforcement service.
 *
 * Provides:
 * - Stable request body hashing (SHA-256 of METHOD + PATH + body)
 * - Find/store via pluggable {@link IdempotencyRepository}
 *
 * Used by {@link zw.gov.mohcc.impilo.companion.filter.IdempotencyFilter}.
 */
public class IdempotencyService {

    private final IdempotencyRepository repository;

    public IdempotencyService(IdempotencyRepository repository) {
        this.repository = repository;
    }

    /**
     * Compute a stable SHA-256 hash from the request method, path, and body.
     */
    public String computeHash(String method, String path, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            if (body != null && body.length > 0) {
                digest.update(body);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Find an existing idempotency record.
     */
    public Optional<IdempotencyRecord> find(String tenantId, String podId, String idempotencyKey) {
        return repository.find(tenantId, podId, idempotencyKey);
    }

    /**
     * Store a new idempotency record.
     */
    public void store(String tenantId, String podId, String idempotencyKey,
                      String requestHash, int responseStatus, String responseBody) {
        repository.store(tenantId, podId, idempotencyKey,
                requestHash, responseStatus, responseBody);
    }
}
