package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.SocialGroupMembershipEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialGroupMembershipRepository extends JpaRepository<SocialGroupMembershipEntity, Long> {

    Optional<SocialGroupMembershipEntity> findByGroupIdAndPersonCpid(UUID groupId, String personCpid);

    List<SocialGroupMembershipEntity> findByTenantIdAndPersonCpidAndStatus(
            UUID tenantId, String personCpid, String status);

    List<SocialGroupMembershipEntity> findByGroupIdOrderByJoinedAtAsc(UUID groupId);

    long countByGroupIdAndStatus(UUID groupId, String status);
}
