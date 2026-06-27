package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.GdhcnReadinessAssessmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GdhcnReadinessAssessmentRepository extends JpaRepository<GdhcnReadinessAssessmentEntity, UUID> {

    List<GdhcnReadinessAssessmentEntity> findByTenantId(UUID tenantId);

    Optional<GdhcnReadinessAssessmentEntity> findByTenantIdAndDomainKey(UUID tenantId, String domainKey);
}
