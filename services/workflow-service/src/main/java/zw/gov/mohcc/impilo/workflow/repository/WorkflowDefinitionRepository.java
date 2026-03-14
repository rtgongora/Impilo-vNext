package zw.gov.mohcc.impilo.workflow.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.workflow.domain.WorkflowDefinitionEntity;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinitionEntity, UUID> {

    @Query("SELECT d FROM WorkflowDefinitionEntity d WHERE d.tenantId = :tenantId"
            + " AND (:status IS NULL OR d.status = :status)"
            + " AND (:category IS NULL OR d.category = :category)")
    Page<WorkflowDefinitionEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                                  @Param("status") String status,
                                                  @Param("category") String category,
                                                  Pageable pageable);

    Optional<WorkflowDefinitionEntity> findByTenantIdAndCodeAndVersion(UUID tenantId, String code, int version);

    @Query("SELECT d FROM WorkflowDefinitionEntity d WHERE d.tenantId = :tenantId"
            + " AND d.updatedAt <= :asOf ORDER BY d.definitionId")
    Page<WorkflowDefinitionEntity> findSnapshotAsOf(@Param("tenantId") UUID tenantId,
                                                      @Param("asOf") OffsetDateTime asOf,
                                                      Pageable pageable);
}
