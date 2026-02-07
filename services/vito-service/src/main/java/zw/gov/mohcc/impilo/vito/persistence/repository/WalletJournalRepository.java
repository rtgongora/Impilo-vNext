package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.WalletJournalEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface WalletJournalRepository extends JpaRepository<WalletJournalEntity, Long> {

    Page<WalletJournalEntity> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    List<WalletJournalEntity> findByTenantIdAndTransactionRef(UUID tenantId, String transactionRef);

    List<WalletJournalEntity> findByTenantIdAndReconciledFalse(UUID tenantId);
}
