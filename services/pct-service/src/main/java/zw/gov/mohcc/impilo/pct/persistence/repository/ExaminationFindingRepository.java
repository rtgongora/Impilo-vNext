package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ExaminationFindingEntity;

import java.util.List;
import java.util.UUID;

public interface ExaminationFindingRepository extends JpaRepository<ExaminationFindingEntity, UUID> {

    List<ExaminationFindingEntity> findByTenantIdAndExaminationIdOrderByRegionAsc(
            UUID tenantId, UUID examinationId);
}
