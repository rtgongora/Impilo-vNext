package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.SocialCommunityEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialCommunityRepository extends JpaRepository<SocialCommunityEntity, Long> {

    Optional<SocialCommunityEntity> findByCommunityIdAndTenantId(UUID communityId, UUID tenantId);

    List<SocialCommunityEntity> findByTenantIdAndStatusOrderByNameAsc(UUID tenantId, String status);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
