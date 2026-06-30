package zw.gov.mohcc.impilo.guidance.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.guidance.persistence.entity.GuidanceItemEntity;

import java.util.List;
import java.util.UUID;

public interface GuidanceItemRepository extends JpaRepository<GuidanceItemEntity, UUID> {

    List<GuidanceItemEntity> findByTenantIdAndStatusOrderByPriorityAsc(String tenantId, String status);

    List<GuidanceItemEntity> findByTenantIdAndDomainAndStatusOrderByPriorityAsc(
            String tenantId, String domain, String status);
}
