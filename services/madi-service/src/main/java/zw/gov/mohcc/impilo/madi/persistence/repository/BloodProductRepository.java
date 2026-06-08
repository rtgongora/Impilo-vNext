package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodProductEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodProductRepository extends JpaRepository<BloodProductEntity, Long> {
    Optional<BloodProductEntity> findByProductIdAndTenantId(UUID productId, UUID tenantId);
    List<BloodProductEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
