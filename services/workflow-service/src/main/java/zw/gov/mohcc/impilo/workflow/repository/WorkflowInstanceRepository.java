package zw.gov.mohcc.impilo.workflow.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.workflow.domain.WorkflowInstanceEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstanceEntity, UUID> {

    @Query("SELECT i FROM WorkflowInstanceEntity i WHERE i.tenantId = :tenantId"
            + " AND (:status IS NULL OR i.status = :status)")
    Page<WorkflowInstanceEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                                @Param("status") String status,
                                                Pageable pageable);

    @Query("SELECT i FROM WorkflowInstanceEntity i WHERE i.tenantId = :tenantId"
            + " AND i.updatedAt <= :asOf ORDER BY i.instanceId")
    Page<WorkflowInstanceEntity> findSnapshotAsOf(@Param("tenantId") UUID tenantId,
                                                    @Param("asOf") OffsetDateTime asOf,
                                                    Pageable pageable);
}
