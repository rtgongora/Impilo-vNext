package zw.gov.mohcc.impilo.community.social.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialGroupMembershipEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialGroupMembershipRepository extends JpaRepository<SocialGroupMembershipEntity, Long> {
    Optional<SocialGroupMembershipEntity> findFirstByGroupIdAndActorId(UUID groupId, String actorId);
    List<SocialGroupMembershipEntity> findByActorIdAndStatus(String actorId, String status);
}
