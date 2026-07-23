package zw.gov.mohcc.impilo.pharmacy.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.domain.ClarificationStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.ClarificationRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClarificationRequestRepository extends JpaRepository<ClarificationRequestEntity, UUID> {

    boolean existsByDispenseOrderIdAndStatus(UUID dispenseOrderId, ClarificationStatus status);

    boolean existsByDispenseItemIdAndStatus(UUID dispenseItemId, ClarificationStatus status);

    List<ClarificationRequestEntity> findByDispenseOrderIdOrderByRequestedAtDesc(UUID dispenseOrderId);

    /** The unconsumed APPROVED clarification that authorises a specific substitution. */
    Optional<ClarificationRequestEntity>
    findFirstByDispenseItemIdAndTargetDrugCodeAndStatusAndAppliedAtIsNull(
            UUID dispenseItemId, String targetDrugCode, ClarificationStatus status);
}
