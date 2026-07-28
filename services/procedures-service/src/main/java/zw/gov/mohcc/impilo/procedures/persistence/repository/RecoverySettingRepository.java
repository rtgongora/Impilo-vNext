package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procedures.persistence.entity.RecoverySettingEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecoverySettingRepository extends JpaRepository<RecoverySettingEntity, UUID> {
    Optional<RecoverySettingEntity> findByTenantIdAndSettingCode(UUID tenantId, String settingCode);
    List<RecoverySettingEntity> findByTenantIdOrderBySettingCodeAsc(UUID tenantId);
}
