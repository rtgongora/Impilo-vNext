package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.CommunityVisitEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityVisitRepository extends JpaRepository<CommunityVisitEntity, UUID> {

    List<CommunityVisitEntity> findByTenantIdAndHouseholdIdOrderByCreatedAtDesc(UUID tenantId, UUID householdId);

    Optional<CommunityVisitEntity> findByTenantIdAndOfflineId(UUID tenantId, String offlineId);
}
