package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.BedEntity;

import java.util.List;
import java.util.UUID;

public interface BedRepository extends JpaRepository<BedEntity, UUID> {

    List<BedEntity> findByTenantIdAndBedClassAndStatusOrderByCodeAsc(UUID tenantId, String bedClass, String status);

    List<BedEntity> findByTenantIdAndBedClassOrderByCodeAsc(UUID tenantId, String bedClass);

    List<BedEntity> findByTenantIdAndStatusOrderByCodeAsc(UUID tenantId, String status);

    List<BedEntity> findByTenantIdOrderByCodeAsc(UUID tenantId);

    long countByTenantIdAndBedClassAndStatus(UUID tenantId, String bedClass, String status);
}
