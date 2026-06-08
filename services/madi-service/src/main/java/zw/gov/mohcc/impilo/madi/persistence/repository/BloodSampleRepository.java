package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodSampleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodSampleRepository extends JpaRepository<BloodSampleEntity, Long> {
    Optional<BloodSampleEntity> findBySampleIdAndTenantId(UUID sampleId, UUID tenantId);
    List<BloodSampleEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
