package zw.gov.mohcc.impilo.mushex.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentAttemptEntity;

import java.util.Optional;

/**
 * Repository for {@link PaymentAttemptEntity} — individual payment attempts
 * against a payment intent via a specific adapter.
 */
@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, String> {

    /**
     * Find an attempt by the external adapter reference.
     * Used during webhook processing to look up the attempt that
     * corresponds to an external payment notification.
     */
    Optional<PaymentAttemptEntity> findByAdapterRef(String adapterRef);
}
