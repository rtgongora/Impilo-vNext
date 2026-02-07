package zw.gov.mohcc.impilo.vito.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.WalletEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<WalletEntity, Long> {

    Optional<WalletEntity> findByTenantIdAndHealthIdAndCurrency(
            UUID tenantId, UUID healthId, String currency);

    /** Pessimistic lock for balance mutations — prevents double-spend */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletEntity> findWithLockByTenantIdAndHealthIdAndCurrency(
            UUID tenantId, UUID healthId, String currency);

    Optional<WalletEntity> findByTenantIdAndHealthId(UUID tenantId, UUID healthId);
}
