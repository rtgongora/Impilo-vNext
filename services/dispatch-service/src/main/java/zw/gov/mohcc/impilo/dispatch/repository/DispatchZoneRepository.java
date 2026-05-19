package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.DispatchZoneEntity;

import java.util.List;
import java.util.UUID;

public interface DispatchZoneRepository extends JpaRepository<DispatchZoneEntity, UUID> {
    List<DispatchZoneEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
