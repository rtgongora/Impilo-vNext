package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodDiscardEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodDiscardRepository extends JpaRepository<BloodDiscardEntity, Long> {
    Optional<BloodDiscardEntity> findByDiscardIdAndTenantId(UUID discardId, UUID tenantId);
    List<BloodDiscardEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
