package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.wallet.persistence.entity.MerchantAccountEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantAccountRepository extends JpaRepository<MerchantAccountEntity, Long> {

    Optional<MerchantAccountEntity> findByTenantIdAndProviderNumber(UUID tenantId, String providerNumber);

    List<MerchantAccountEntity> findByWalletId(UUID walletId);

    Optional<MerchantAccountEntity> findByMerchantId(UUID merchantId);
}
