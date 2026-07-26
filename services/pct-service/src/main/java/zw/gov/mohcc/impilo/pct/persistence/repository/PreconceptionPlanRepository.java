package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.PreconceptionPlanEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreconceptionPlanRepository extends JpaRepository<PreconceptionPlanEntity, UUID> {

    @Query("SELECT p FROM PreconceptionPlanEntity p "
            + "WHERE p.tenantId = :tenantId AND p.subjectCpid = :subjectCpid AND p.status = 'ACTIVE'")
    Optional<PreconceptionPlanEntity> findActive(@Param("tenantId") UUID tenantId,
                                                 @Param("subjectCpid") String subjectCpid);

    @Query("SELECT p FROM PreconceptionPlanEntity p "
            + "WHERE p.tenantId = :tenantId AND p.subjectCpid = :subjectCpid "
            + "ORDER BY p.openedOn DESC")
    List<PreconceptionPlanEntity> findBySubject(@Param("tenantId") UUID tenantId,
                                                @Param("subjectCpid") String subjectCpid);

    Optional<PreconceptionPlanEntity> findByTenantIdAndClientOfflineId(UUID tenantId,
                                                                       String clientOfflineId);
}
