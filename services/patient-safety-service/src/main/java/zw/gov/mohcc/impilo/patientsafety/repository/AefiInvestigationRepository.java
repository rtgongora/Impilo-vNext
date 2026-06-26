package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.AefiInvestigationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AefiInvestigationRepository extends JpaRepository<AefiInvestigationEntity, UUID> {
    Optional<AefiInvestigationEntity> findByCaseId(UUID caseId);
    Optional<AefiInvestigationEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    long countByTenantIdAndInvestigationReferenceStartingWith(UUID tenantId, String prefix);
    List<AefiInvestigationEntity> findByTenantIdAndStatusInOrderByCreatedAtDesc(UUID tenantId, List<String> statuses);
}
