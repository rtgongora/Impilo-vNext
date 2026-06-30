package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.CoachingRelationshipEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoachingRelationshipRepository extends JpaRepository<CoachingRelationshipEntity, Long> {

    List<CoachingRelationshipEntity> findByTenantIdAndPersonCpidOrderByCreatedAtDesc(UUID tenantId, String personCpid);

    List<CoachingRelationshipEntity> findByTenantIdAndCoachProviderIdOrderByCreatedAtDesc(UUID tenantId, String coachProviderId);

    Optional<CoachingRelationshipEntity> findByRelationshipIdAndTenantId(UUID relationshipId, UUID tenantId);

    Optional<CoachingRelationshipEntity> findByTenantIdAndPersonCpidAndCoachProviderIdAndStatus(
            UUID tenantId, String personCpid, String coachProviderId, String status);
}
