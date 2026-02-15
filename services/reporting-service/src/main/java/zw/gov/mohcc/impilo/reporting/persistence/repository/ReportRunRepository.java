package zw.gov.mohcc.impilo.reporting.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportRunEntity;

import java.util.UUID;

@Repository
public interface ReportRunRepository extends JpaRepository<ReportRunEntity, Long> {

    Page<ReportRunEntity> findByTenantIdAndDefinitionId(UUID tenantId, Long definitionId, Pageable pageable);
}
