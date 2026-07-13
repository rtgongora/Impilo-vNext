package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionRequestEntity;

import java.util.Optional;
import java.util.UUID;

public interface BillContributionRequestRepository extends JpaRepository<BillContributionRequestEntity, UUID> {
    Optional<BillContributionRequestEntity> findByShareToken(String shareToken);
}
