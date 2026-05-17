package zw.gov.mohcc.impilo.community.social.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialCommunityMembershipEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialCommunityMembershipRepository extends JpaRepository<SocialCommunityMembershipEntity, Long> {

    Optional<SocialCommunityMembershipEntity> findFirstByCommunityIdAndActorId(UUID communityId, String actorId);

    List<SocialCommunityMembershipEntity> findByActorIdAndStatus(String actorId, String status);

    long countByCommunityIdAndStatus(UUID communityId, String status);
}
