package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.BenefitMovementEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BenefitMovementRepository extends JpaRepository<BenefitMovementEntity, UUID> {
    Optional<BenefitMovementEntity> findByAccumulatorIdAndReference(UUID accumulatorId, String reference);
}
