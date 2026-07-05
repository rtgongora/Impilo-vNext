package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaChapterEntity;

public interface MediaChapterRepository extends JpaRepository<MediaChapterEntity, UUID> {

    List<MediaChapterEntity> findByTenantIdAndAssetIdOrderByChapterOrderAscStartSecondsAsc(
            UUID tenantId, UUID assetId);

    Optional<MediaChapterEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
