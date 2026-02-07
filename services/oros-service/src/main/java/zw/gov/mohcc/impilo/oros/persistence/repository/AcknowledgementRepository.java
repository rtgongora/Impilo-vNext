package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.domain.AckType;
import zw.gov.mohcc.impilo.oros.persistence.entity.AcknowledgementEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcknowledgementRepository extends JpaRepository<AcknowledgementEntity, UUID> {

    List<AcknowledgementEntity> findByOrderIdOrderByAckedAtAsc(String orderId);

    /**
     * Finds all acknowledgements for an order (unordered).
     *
     * @param orderId the ULID order identifier
     * @return list of acknowledgement entities
     */
    List<AcknowledgementEntity> findByOrderId(String orderId);

    Optional<AcknowledgementEntity> findByOrderIdAndAckType(String orderId, AckType ackType);

    /**
     * Checks whether an acknowledgement of a specific type exists for an order.
     *
     * @param orderId the ULID order identifier
     * @param ackType the acknowledgement type to check
     * @return true if at least one matching acknowledgement exists
     */
    boolean existsByOrderIdAndAckType(String orderId, AckType ackType);
}
