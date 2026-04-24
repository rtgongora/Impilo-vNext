package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.VisibilityEscalationRequestEntity;

import java.util.List;
import java.util.UUID;

public interface VisibilityEscalationRequestRepository extends JpaRepository<VisibilityEscalationRequestEntity, Long> {

    List<VisibilityEscalationRequestEntity> findByTenantIdAndActorIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, String actorId, String status);
}
