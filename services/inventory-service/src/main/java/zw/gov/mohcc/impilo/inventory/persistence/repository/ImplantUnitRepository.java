package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ImplantUnitEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for implant device units (inv_implant_registry).
 */
@Repository
public interface ImplantUnitRepository extends JpaRepository<ImplantUnitEntity, UUID> {

    Optional<ImplantUnitEntity> findByTenantIdAndUdi(UUID tenantId, String udi);

    List<ImplantUnitEntity> findByTenantIdAndLotNumber(UUID tenantId, String lotNumber);
}
