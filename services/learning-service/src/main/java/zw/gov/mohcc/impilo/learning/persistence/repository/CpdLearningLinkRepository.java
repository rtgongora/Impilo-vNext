package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.CpdLearningLinkEntity;

public interface CpdLearningLinkRepository extends JpaRepository<CpdLearningLinkEntity, UUID> {

    List<CpdLearningLinkEntity> findByTenantIdAndActiveTrueAndResourceId(UUID tenantId, UUID resourceId);
}
