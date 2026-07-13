package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillContributionRepository extends JpaRepository<BillContributionEntity, UUID> {
    List<BillContributionEntity> findByRequestIdOrderByCreatedAtDesc(UUID requestId);
    Optional<BillContributionEntity> findByIdempotencyKey(String idempotencyKey);
}
