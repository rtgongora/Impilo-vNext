package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.VerbalAutopsyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerbalAutopsyRepository extends JpaRepository<VerbalAutopsyEntity, UUID> {
    List<VerbalAutopsyEntity> findByTenantIdAndCaseIdOrderByCreatedAtDesc(UUID tenantId, UUID caseId);
    Optional<VerbalAutopsyEntity> findByTenantIdAndVaId(UUID tenantId, UUID vaId);
}
