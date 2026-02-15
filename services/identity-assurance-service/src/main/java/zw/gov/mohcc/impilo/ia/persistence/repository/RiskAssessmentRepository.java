package zw.gov.mohcc.impilo.ia.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ia.persistence.entity.RiskAssessmentEntity;

@Repository
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessmentEntity, Long> {

    List<RiskAssessmentEntity> findByTenantId(UUID tenantId);

    List<RiskAssessmentEntity> findByTenantIdAndActorId(UUID tenantId, String actorId);
}
