package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.wallet.persistence.entity.DepositIntentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepositIntentRepository extends JpaRepository<DepositIntentEntity, Long> {

    Optional<DepositIntentEntity> findByDepositId(UUID depositId);

    Optional<DepositIntentEntity> findByReferenceCode(String referenceCode);

    List<DepositIntentEntity> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    List<DepositIntentEntity> findByStatusOrderByCreatedAtAsc(String status);
}
