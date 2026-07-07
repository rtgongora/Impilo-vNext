package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.IntegrationEventEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntegrationEventRepository extends JpaRepository<IntegrationEventEntity, Long> {

    List<IntegrationEventEntity> findByTenantIdAndTargetOwnerAndStatus(
            UUID tenantId, String targetOwner, String status);

    List<IntegrationEventEntity> findByTenantIdAndPersonCpidOrderByCreatedAtDesc(UUID tenantId, String personCpid);
}
