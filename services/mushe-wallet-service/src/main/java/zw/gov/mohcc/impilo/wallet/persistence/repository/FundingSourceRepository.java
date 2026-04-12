package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.wallet.persistence.entity.FundingSourceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundingSourceRepository extends JpaRepository<FundingSourceEntity, Long> {

    List<FundingSourceEntity> findByWalletId(UUID walletId);

    Optional<FundingSourceEntity> findBySourceId(UUID sourceId);
}
