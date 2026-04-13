package zw.gov.mohcc.impilo.procurement.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procurement.persistence.entity.SupplierEntity;

import java.util.List;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<SupplierEntity, UUID> {
    List<SupplierEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
}
