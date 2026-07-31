package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.GeneticRiskEntity;

import java.util.List;
import java.util.UUID;

public interface GeneticRiskRepository extends JpaRepository<GeneticRiskEntity, UUID> {
    List<GeneticRiskEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
