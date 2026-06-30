package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.PresenceEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PresenceRepository extends JpaRepository<PresenceEntity, UUID> {

    Optional<PresenceEntity> findByTenantIdAndActorId(UUID tenantId, String actorId);

    List<PresenceEntity> findByTenantIdAndActorIdIn(UUID tenantId, Collection<String> actorIds);

    /** On-call roster: workers with a given duty status (e.g. ON_CALL) for a tenant. */
    List<PresenceEntity> findByTenantIdAndDutyStatusOrderByUpdatedAtDesc(UUID tenantId, String dutyStatus);
}
