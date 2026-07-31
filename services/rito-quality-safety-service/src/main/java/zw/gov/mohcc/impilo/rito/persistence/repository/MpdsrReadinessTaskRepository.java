package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.rito.persistence.entity.MpdsrReadinessTaskEntity;

import java.util.List;
import java.util.UUID;

public interface MpdsrReadinessTaskRepository extends JpaRepository<MpdsrReadinessTaskEntity, UUID> {

    List<MpdsrReadinessTaskEntity> findByReviewIdOrderByCreatedAtDesc(UUID reviewId);

    boolean existsByTenantIdAndTaskReference(UUID tenantId, String taskReference);
}
