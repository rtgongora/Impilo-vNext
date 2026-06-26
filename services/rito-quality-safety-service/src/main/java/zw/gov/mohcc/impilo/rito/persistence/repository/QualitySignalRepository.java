package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.QualitySignalEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QualitySignalRepository extends JpaRepository<QualitySignalEntity, UUID> {

    Optional<QualitySignalEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<QualitySignalEntity> findByTenantIdAndStatusOrderByReceivedAtDesc(UUID tenantId, String status);

    List<QualitySignalEntity> findByTenantIdOrderByReceivedAtDesc(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
