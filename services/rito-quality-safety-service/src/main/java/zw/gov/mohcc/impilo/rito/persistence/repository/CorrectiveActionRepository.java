package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.CorrectiveActionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CorrectiveActionRepository extends JpaRepository<CorrectiveActionEntity, UUID> {

    Optional<CorrectiveActionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CorrectiveActionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<CorrectiveActionEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    List<CorrectiveActionEntity> findByCaseId(UUID caseId);

    List<CorrectiveActionEntity> findByTenantIdAndOwnerActorId(UUID tenantId, String ownerActorId);

    boolean existsByTenantIdAndCaReference(UUID tenantId, String caReference);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
