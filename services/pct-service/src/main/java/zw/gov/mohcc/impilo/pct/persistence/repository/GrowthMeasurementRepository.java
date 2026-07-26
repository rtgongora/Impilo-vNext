package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.GrowthMeasurementEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface GrowthMeasurementRepository extends JpaRepository<GrowthMeasurementEntity, UUID> {

    List<GrowthMeasurementEntity> findByTenantIdAndSubjectCpidOrderByMeasuredAtDesc(UUID tenantId, String subjectCpid);

    List<GrowthMeasurementEntity> findByTenantIdAndSubjectCpidOrderByMeasuredAtAsc(UUID tenantId, String subjectCpid);
}
