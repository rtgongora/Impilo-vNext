package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaBookmarkEntity;

public interface MediaBookmarkRepository extends JpaRepository<MediaBookmarkEntity, UUID> {

    List<MediaBookmarkEntity> findByTenantIdAndEnrolmentIdAndAssetIdOrderByPositionSecondsAsc(
            UUID tenantId, UUID enrolmentId, UUID assetId);

    Optional<MediaBookmarkEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
