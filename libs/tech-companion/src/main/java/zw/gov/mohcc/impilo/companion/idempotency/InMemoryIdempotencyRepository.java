package zw.gov.mohcc.impilo.companion.idempotency;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link IdempotencyRepository} for testing.
 */
public class InMemoryIdempotencyRepository implements IdempotencyRepository {

    private final ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> find(String tenantId, String podId, String idempotencyKey) {
        String key = compositeKey(tenantId, podId, idempotencyKey);
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void store(String tenantId, String podId, String idempotencyKey,
                      String requestHash, int responseStatus, String responseBody) {
        String key = compositeKey(tenantId, podId, idempotencyKey);
        store.putIfAbsent(key, new IdempotencyRecord(
                tenantId, podId, idempotencyKey, requestHash,
                responseStatus, responseBody,
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(24)
        ));
    }

    /** Clear all stored records (for test reset). */
    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    private static String compositeKey(String tenantId, String podId, String idempotencyKey) {
        return tenantId + "|" + podId + "|" + idempotencyKey;
    }
}
