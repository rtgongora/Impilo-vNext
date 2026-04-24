package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.WalletAccountEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletAccountRepository extends JpaRepository<WalletAccountEntity, String> {

    List<WalletAccountEntity> findByTenantIdAndOwnerRef(UUID tenantId, String ownerRef);

    Optional<WalletAccountEntity> findByTenantIdAndWalletCode(UUID tenantId, String walletCode);
}
