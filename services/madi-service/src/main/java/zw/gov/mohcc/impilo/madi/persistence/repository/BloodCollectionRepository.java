package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodCollectionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodCollectionRepository extends JpaRepository<BloodCollectionEntity, Long> {
    Optional<BloodCollectionEntity> findByCollectionIdAndTenantId(UUID collectionId, UUID tenantId);
    List<BloodCollectionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
