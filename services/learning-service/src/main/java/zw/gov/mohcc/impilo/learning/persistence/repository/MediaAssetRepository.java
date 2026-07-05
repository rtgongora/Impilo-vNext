package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaAssetEntity;

public interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {

    Optional<MediaAssetEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Replay-adoption idempotency key: one asset per (tenant, live event). */
    Optional<MediaAssetEntity> findByTenantIdAndLiveEventId(UUID tenantId, UUID liveEventId);

    /** Newest attached asset backing a lesson (playback resolution). */
    Optional<MediaAssetEntity> findFirstByTenantIdAndSourceLessonIdOrderByCreatedAtDesc(
            UUID tenantId, UUID sourceLessonId);

    /** Course-scoped assets — WATCH_THRESHOLD rule evaluation input. */
    List<MediaAssetEntity> findByTenantIdAndCourseId(UUID tenantId, UUID courseId);

    /** Replay authoring queue: adopted assets waiting for lesson attachment. */
    List<MediaAssetEntity> findByTenantIdAndUnattachedTrueOrderByCreatedAtDesc(UUID tenantId);
}
