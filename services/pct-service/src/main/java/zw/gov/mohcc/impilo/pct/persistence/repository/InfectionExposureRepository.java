package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.InfectionExposureEntity;

import java.util.List;
import java.util.UUID;

public interface InfectionExposureRepository extends JpaRepository<InfectionExposureEntity, UUID> {
    List<InfectionExposureEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
