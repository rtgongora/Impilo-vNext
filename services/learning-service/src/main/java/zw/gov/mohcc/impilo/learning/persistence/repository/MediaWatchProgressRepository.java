package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaWatchProgressEntity;

public interface MediaWatchProgressRepository extends JpaRepository<MediaWatchProgressEntity, UUID> {

    Optional<MediaWatchProgressEntity> findByEnrolmentIdAndAssetId(UUID enrolmentId, UUID assetId);

    List<MediaWatchProgressEntity> findByTenantIdAndEnrolmentId(UUID tenantId, UUID enrolmentId);
}
