package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    Page<TransactionEntity> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    Optional<TransactionEntity> findByReference(String reference);

    Optional<TransactionEntity> findByIdempotencyKey(String idempotencyKey);
}
