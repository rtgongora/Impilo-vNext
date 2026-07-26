package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdvanceDirectiveEntity;

import java.util.List;
import java.util.UUID;

public interface AdvanceDirectiveRepository extends JpaRepository<AdvanceDirectiveEntity, UUID> {

    List<AdvanceDirectiveEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
