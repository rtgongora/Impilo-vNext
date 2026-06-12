package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodInventoryBalanceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodInventoryBalanceRepository extends JpaRepository<BloodInventoryBalanceEntity, Long> {
    Optional<BloodInventoryBalanceEntity> findByBalanceIdAndTenantId(UUID balanceId, UUID tenantId);
    Optional<BloodInventoryBalanceEntity> findByTenantIdAndBloodBankIdAndInventoryItemCodeAndBloodGroupAndComponentType(
            UUID tenantId, UUID bloodBankId, String inventoryItemCode, String bloodGroup, String componentType);
    List<BloodInventoryBalanceEntity> findByTenantIdAndBloodBankId(UUID tenantId, UUID bloodBankId);
    List<BloodInventoryBalanceEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
}
