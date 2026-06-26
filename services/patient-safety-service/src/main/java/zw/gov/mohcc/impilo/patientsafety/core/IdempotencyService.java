package zw.gov.mohcc.impilo.patientsafety.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.patientsafety.domain.IdempotencyKeyEntity;
import zw.gov.mohcc.impilo.patientsafety.repository.IdempotencyKeyRepository;

import java.util.Optional;

/**
 * Lightweight idempotency for mutating endpoints. Callers pass the {@code Idempotency-Key} header;
 * a prior record for the same (tenant, pod, key) short-circuits and returns the stored response so
 * retries do not create duplicate reports/cases. Records are best-effort and stored post-commit.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    /** Returns a previously stored response body for this key, if any. */
    @Transactional(readOnly = true)
    public Optional<IdempotencyKeyEntity> find(String tenantId, String podId, String idempotencyKey) {
        if (isBlank(idempotencyKey)) {
            return Optional.empty();
        }
        return repository.findById(new IdempotencyKeyEntity.PK(tenantId, safePod(podId), idempotencyKey));
    }

    /** Stores the response for a key so subsequent replays are deduplicated. No-op if key is blank. */
    @Transactional
    public void record(String tenantId, String podId, String idempotencyKey,
                       String requestHash, int responseStatus, String responseBody) {
        if (isBlank(idempotencyKey)) {
            return;
        }
        String pod = safePod(podId);
        if (repository.existsById(new IdempotencyKeyEntity.PK(tenantId, pod, idempotencyKey))) {
            return;
        }
        repository.save(new IdempotencyKeyEntity(
                tenantId, pod, idempotencyKey,
                requestHash == null ? "" : requestHash,
                responseStatus,
                responseBody == null ? "" : responseBody));
    }

    private static String safePod(String podId) {
        return (podId == null || podId.isBlank()) ? "national-spine" : podId;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
