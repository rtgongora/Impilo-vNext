package zw.gov.mohcc.impilo.ubomi.integration.rg;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RgVerificationRepository extends JpaRepository<RgVerificationEntity, Long> {

    /** Idempotency lookup — tolerates at-least-once redelivery of the same request. */
    Optional<RgVerificationEntity> findByTenantIdAndBusinessKey(UUID tenantId, String businessKey);

    /** Reconcile-loop driver: parked attempts, oldest first. */
    List<RgVerificationEntity> findTop100ByStatusOrderByCreatedAtAsc(String status);
}
