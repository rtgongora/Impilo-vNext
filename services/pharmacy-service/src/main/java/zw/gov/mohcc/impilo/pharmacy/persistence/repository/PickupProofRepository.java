package zw.gov.mohcc.impilo.pharmacy.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PickupProofEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PickupProofRepository extends JpaRepository<PickupProofEntity, UUID> {

    Optional<PickupProofEntity> findByTokenHashAndStatus(String tokenHash, PickupStatus status);

    List<PickupProofEntity> findByDispenseOrderId(UUID dispenseOrderId);

    List<PickupProofEntity> findByStatusAndExpiresAtBefore(PickupStatus status, OffsetDateTime cutoff);

    /** OF-B16 named-recipient/SMS path: resolve an opaque SMS reference server-side. */
    Optional<PickupProofEntity> findBySmsReferenceHashAndStatus(String smsReferenceHash, PickupStatus status);
}
