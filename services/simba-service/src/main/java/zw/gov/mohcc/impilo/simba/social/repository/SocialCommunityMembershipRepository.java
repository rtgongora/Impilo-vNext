package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.SocialCommunityMembershipEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialCommunityMembershipRepository
        extends JpaRepository<SocialCommunityMembershipEntity, Long> {

    Optional<SocialCommunityMembershipEntity> findByCommunityIdAndPersonCpid(
            UUID communityId, String personCpid);

    List<SocialCommunityMembershipEntity> findByTenantIdAndPersonCpidAndStatus(
            UUID tenantId, String personCpid, String status);

    List<SocialCommunityMembershipEntity> findByCommunityIdOrderByJoinedAtAsc(UUID communityId);

    long countByCommunityIdAndStatus(UUID communityId, String status);
}
