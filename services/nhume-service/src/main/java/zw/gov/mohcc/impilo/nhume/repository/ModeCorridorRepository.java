package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.ModeCorridorEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModeCorridorRepository extends JpaRepository<ModeCorridorEntity, UUID> {

    List<ModeCorridorEntity> findByTenantIdAndModeAndEnabledTrueOrderByCorridorCodeAsc(
            UUID tenantId, String mode);

    List<ModeCorridorEntity> findByTenantIdOrderByCorridorCodeAsc(UUID tenantId);

    Optional<ModeCorridorEntity> findByTenantIdAndCorridorCode(UUID tenantId, String corridorCode);
}
