package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.learning.persistence.entity.WorkflowLearningLinkEntity;

public interface WorkflowLearningLinkRepository extends JpaRepository<WorkflowLearningLinkEntity, UUID> {

    @Query(
            """
            select w from WorkflowLearningLinkEntity w
            where w.tenantId = :tenantId and w.active = true and w.appCode = :appCode
            and w.screenOrRouteRef = :routeRef
            """)
    List<WorkflowLearningLinkEntity> findForAppAndRoute(
            @Param("tenantId") UUID tenantId, @Param("appCode") String appCode, @Param("routeRef") String routeRef);

    List<WorkflowLearningLinkEntity> findByTenantIdAndActiveTrueAndAppCode(UUID tenantId, String appCode);
}
