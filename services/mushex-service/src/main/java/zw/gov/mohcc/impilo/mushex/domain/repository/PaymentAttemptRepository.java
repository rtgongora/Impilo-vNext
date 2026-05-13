package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentAttemptEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, String> {

    List<PaymentAttemptEntity> findByIntentId(String intentId);

    /**
     * Phase 3 follow-on — chronological list for the {@code GET /payment-intents/{id}/attempts}
     * read endpoint and the equivalent BFF passthrough.
     */
    List<PaymentAttemptEntity> findByIntentIdOrderByRequestedAtAsc(String intentId);

    /**
     * Find an attempt by the external adapter reference (webhook correlation).
     */
    Optional<PaymentAttemptEntity> findByAdapterRef(String adapterRef);

    /**
     * Find a previously-created attempt by (intent, caller idempotency key).
     * Used by {@code PaymentAttemptService} (Phase 3) to short-circuit replays.
     */
    Optional<PaymentAttemptEntity> findByIntentIdAndIdempotencyKey(String intentId, String idempotencyKey);
}
