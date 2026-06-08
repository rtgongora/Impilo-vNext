package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventRoleAssignmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LiveEventRoleAssignmentRepository extends JpaRepository<LiveEventRoleAssignmentEntity, UUID> {

    List<LiveEventRoleAssignmentEntity> findByEventId(UUID eventId);

    List<LiveEventRoleAssignmentEntity> findByEventIdAndRole(UUID eventId, String role);

    Optional<LiveEventRoleAssignmentEntity> findByEventIdAndUserIdAndRole(UUID eventId, String userId, String role);

    List<LiveEventRoleAssignmentEntity> findByUserIdAndRole(String userId, String role);
}
