package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.khuluma.domain.EscalationEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscalationRepository extends JpaRepository<EscalationEntity, UUID> {

    Optional<EscalationEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<EscalationEntity> findByTenantIdAndStatusInOrderByCreatedAtAsc(UUID tenantId, List<String> statuses);

    List<EscalationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** Open escalations whose first-response target has passed and that haven't been responded to yet. */
    @Query("SELECT e FROM EscalationEntity e WHERE e.firstResponseBreached = false "
            + "AND e.firstRespondedAt IS NULL AND e.firstResponseDueAt < :now "
            + "AND e.status IN ('OPEN','ASSIGNED')")
    List<EscalationEntity> findFirstResponseBreaches(@Param("now") OffsetDateTime now);

    /** Open escalations whose resolution target has passed and that aren't resolved/terminal yet. */
    @Query("SELECT e FROM EscalationEntity e WHERE e.resolutionBreached = false "
            + "AND e.resolutionDueAt < :now AND e.status IN ('OPEN','ASSIGNED','ACCEPTED')")
    List<EscalationEntity> findResolutionBreaches(@Param("now") OffsetDateTime now);
}
