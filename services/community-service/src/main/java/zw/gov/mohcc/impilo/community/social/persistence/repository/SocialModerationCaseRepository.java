package zw.gov.mohcc.impilo.community.social.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialModerationCaseEntity;

import java.util.List;
import java.util.UUID;

public interface SocialModerationCaseRepository extends JpaRepository<SocialModerationCaseEntity, UUID> {
    List<SocialModerationCaseEntity> findByTenantIdAndStatusOrderByCreatedAtAsc(UUID tenantId, String status);
}
