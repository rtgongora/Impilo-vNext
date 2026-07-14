package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaseRepository extends JpaRepository<CaseEntity, UUID> {

    Optional<CaseEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CaseEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    org.springframework.data.domain.Page<CaseEntity> findByTenantId(UUID tenantId, org.springframework.data.domain.Pageable p);

    List<CaseEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    List<CaseEntity> findByTenantIdAndCaseType(UUID tenantId, String caseType);

    List<CaseEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);

    List<CaseEntity> findByTenantIdAndAssignedToActorId(UUID tenantId, String assignedToActorId);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    boolean existsByTenantIdAndCaseReference(UUID tenantId, String caseReference);

    java.util.Optional<CaseEntity> findByTenantIdAndClaimCodeHash(UUID tenantId, String claimCodeHash);
}
