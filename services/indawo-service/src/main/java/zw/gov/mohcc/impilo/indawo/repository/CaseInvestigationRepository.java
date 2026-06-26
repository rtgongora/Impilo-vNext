package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.CaseInvestigationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseInvestigationRepository extends JpaRepository<CaseInvestigationEntity, Long> {
    List<CaseInvestigationEntity> findByTenantIdAndOutbreakIdOrderByCreatedAtDesc(UUID tenantId, UUID outbreakId);
    List<CaseInvestigationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<CaseInvestigationEntity> findByTenantIdAndCaseId(UUID tenantId, UUID caseId);
    long countByTenantIdAndOutbreakId(UUID tenantId, UUID outbreakId);
}
