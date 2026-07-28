package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ResultActionEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ResultActionRepository extends JpaRepository<ResultActionEntity, UUID> {

    List<ResultActionEntity> findByTenantIdAndSubjectCpidOrderByActionedAtDesc(UUID tenantId, String subjectCpid);

    List<ResultActionEntity> findByTenantIdAndResultRefIn(UUID tenantId, Collection<String> resultRefs);
}
