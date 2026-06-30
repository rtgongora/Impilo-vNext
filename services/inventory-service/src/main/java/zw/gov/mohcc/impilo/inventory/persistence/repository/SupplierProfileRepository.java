package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierProfileEntity;

import java.util.List;
import java.util.UUID;

/** Repository for supplier profiles. */
@Repository
public interface SupplierProfileRepository extends JpaRepository<SupplierProfileEntity, UUID> {

    List<SupplierProfileEntity> findByTenantIdOrderByName(UUID tenantId);
}
