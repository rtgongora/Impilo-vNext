package zw.gov.mohcc.impilo.air.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.air.persistence.entity.DriftEventEntity;

@Repository
public interface DriftEventRepository extends JpaRepository<DriftEventEntity, Long> {

    List<DriftEventEntity> findByTenantIdAndModelIdOrderByDetectedAtDesc(UUID tenantId, UUID modelId);
}
