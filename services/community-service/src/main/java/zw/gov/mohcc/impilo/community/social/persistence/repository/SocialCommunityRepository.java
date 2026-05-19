package zw.gov.mohcc.impilo.community.social.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialCommunityEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialCommunityRepository extends JpaRepository<SocialCommunityEntity, UUID> {

    List<SocialCommunityEntity> findByTenantIdOrderByMemberCountDesc(UUID tenantId);

    List<SocialCommunityEntity> findByTenantIdAndCategoryOrderByMemberCountDesc(UUID tenantId, String category);

    Optional<SocialCommunityEntity> findFirstByTenantIdAndSlug(UUID tenantId, String slug);
}
