package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.WalletTransactionEntity;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransactionEntity, String> {

    List<WalletTransactionEntity> findByWalletIdOrderByCreatedAtDesc(String walletId);
}
