package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;

import java.util.List;
import java.util.UUID;

public interface ProblemRepository extends JpaRepository<ProblemEntity, UUID> {

    List<ProblemEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);

    List<ProblemEntity> findByTenantIdAndSubjectCpidAndClinicalStatusOrderByCreatedAtDesc(
            UUID tenantId, String subjectCpid, String clinicalStatus);
}
