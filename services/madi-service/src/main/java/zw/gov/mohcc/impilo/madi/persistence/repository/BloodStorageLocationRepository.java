package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodStorageLocationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodStorageLocationRepository extends JpaRepository<BloodStorageLocationEntity, Long> {
    Optional<BloodStorageLocationEntity> findByLocationIdAndTenantId(UUID locationId, UUID tenantId);
    List<BloodStorageLocationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
