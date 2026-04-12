package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<WalletEntity, Long> {

    Optional<WalletEntity> findByTenantIdAndOwnerTypeAndOwnerRef(UUID tenantId, String ownerType, String ownerRef);

    Optional<WalletEntity> findByWalletId(UUID walletId);

    Optional<WalletEntity> findByOwnerRefAndOwnerType(String ownerRef, String ownerType);
}
