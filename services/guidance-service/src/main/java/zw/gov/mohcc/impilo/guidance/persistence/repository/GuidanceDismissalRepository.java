package zw.gov.mohcc.impilo.guidance.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.guidance.persistence.entity.GuidanceDismissalEntity;

import java.util.List;
import java.util.UUID;

public interface GuidanceDismissalRepository extends JpaRepository<GuidanceDismissalEntity, UUID> {

    List<GuidanceDismissalEntity> findByTenantIdAndActorId(String tenantId, String actorId);

    boolean existsByTenantIdAndActorIdAndGuidanceKey(String tenantId, String actorId, String guidanceKey);
}
