package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.learning.persistence.entity.ContextualLearningHintEntity;

public interface ContextualLearningHintRepository extends JpaRepository<ContextualLearningHintEntity, UUID> {

    @Query(
            """
            select h from ContextualLearningHintEntity h
            where h.tenantId = :tenantId and h.active = true and h.appCode = :appCode
            and h.routeOrComponentRef = :routeRef
            """)
    List<ContextualLearningHintEntity> findForAppAndRoute(
            @Param("tenantId") UUID tenantId, @Param("appCode") String appCode, @Param("routeRef") String routeRef);
}
