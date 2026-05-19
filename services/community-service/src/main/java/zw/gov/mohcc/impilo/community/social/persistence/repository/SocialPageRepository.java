package zw.gov.mohcc.impilo.community.social.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialPageEntity;

import java.util.List;
import java.util.UUID;

public interface SocialPageRepository extends JpaRepository<SocialPageEntity, UUID> {
    List<SocialPageEntity> findByTenantIdOrderByFollowerCountDesc(UUID tenantId);
    List<SocialPageEntity> findByTenantIdAndKindOrderByFollowerCountDesc(UUID tenantId, String kind);
}
