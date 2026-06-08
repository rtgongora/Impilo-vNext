package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodReturnEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodReturnRepository extends JpaRepository<BloodReturnEntity, Long> {
    Optional<BloodReturnEntity> findByReturnIdAndTenantId(UUID returnId, UUID tenantId);
    List<BloodReturnEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
