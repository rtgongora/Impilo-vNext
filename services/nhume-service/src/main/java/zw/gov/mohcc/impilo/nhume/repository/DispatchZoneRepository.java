package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DispatchZoneEntity;

import java.util.List;
import java.util.UUID;

public interface DispatchZoneRepository extends JpaRepository<DispatchZoneEntity, UUID> {
    List<DispatchZoneEntity> findByTenantIdAndActiveTrue(UUID tenantId);
}
