package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.PastProcedureEntity;

import java.util.List;
import java.util.UUID;

public interface PastProcedureRepository extends JpaRepository<PastProcedureEntity, UUID> {

    List<PastProcedureEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
