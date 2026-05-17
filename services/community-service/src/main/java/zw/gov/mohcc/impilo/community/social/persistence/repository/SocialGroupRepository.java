package zw.gov.mohcc.impilo.community.social.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialGroupEntity;

import java.util.List;
import java.util.UUID;

public interface SocialGroupRepository extends JpaRepository<SocialGroupEntity, UUID> {

    List<SocialGroupEntity> findByTenantIdOrderByMemberCountDesc(UUID tenantId);

    List<SocialGroupEntity> findByTenantIdAndCommunityIdOrderByMemberCountDesc(UUID tenantId, UUID communityId);
}
