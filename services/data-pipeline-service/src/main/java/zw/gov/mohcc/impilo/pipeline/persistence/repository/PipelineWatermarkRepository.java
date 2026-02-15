package zw.gov.mohcc.impilo.pipeline.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.PipelineWatermarkEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PipelineWatermarkRepository extends JpaRepository<PipelineWatermarkEntity, Long> {

    Optional<PipelineWatermarkEntity> findByTenantIdAndSourceId(UUID tenantId, String sourceId);

    List<PipelineWatermarkEntity> findByTenantId(UUID tenantId);
}
