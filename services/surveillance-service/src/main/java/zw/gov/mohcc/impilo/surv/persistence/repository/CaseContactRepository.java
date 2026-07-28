package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.CaseContactEntity;

public interface CaseContactRepository extends JpaRepository<CaseContactEntity, Long> {

    List<CaseContactEntity> findByTenantIdAndCaseIdOrderByCreatedAtDesc(UUID tenantId, Long caseId);

    /** Outstanding tracing work — the query a response team runs each morning. */
    List<CaseContactEntity> findByTenantIdAndTraceStatusNotOrderByCreatedAtAsc(UUID tenantId, String traceStatus);

    long countByTenantIdAndCaseId(UUID tenantId, Long caseId);
}
