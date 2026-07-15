package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.FollowUpActionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FollowUpActionRepository extends JpaRepository<FollowUpActionEntity, Long> {

    Optional<FollowUpActionEntity> findByFollowUpIdAndTenantId(UUID followUpId, UUID tenantId);

    List<FollowUpActionEntity> findByTenantIdAndPersonCpidOrderByCreatedAtDesc(UUID tenantId, String personCpid);

    List<FollowUpActionEntity> findByTenantIdAndCreatedByProviderOrderByCreatedAtDesc(
            UUID tenantId, String createdByProvider);
}
