package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.TransfusionObservationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransfusionObservationRepository extends JpaRepository<TransfusionObservationEntity, Long> {
    Optional<TransfusionObservationEntity> findByObservationIdAndTenantId(UUID observationId, UUID tenantId);
    List<TransfusionObservationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
