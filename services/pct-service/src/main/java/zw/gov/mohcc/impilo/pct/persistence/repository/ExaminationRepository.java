package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ExaminationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExaminationRepository extends JpaRepository<ExaminationEntity, UUID> {

    List<ExaminationEntity> findByTenantIdAndSubjectCpidOrderByExaminedAtDesc(UUID tenantId, String subjectCpid);

    Optional<ExaminationEntity> findByTenantIdAndExaminationId(UUID tenantId, UUID examinationId);

    Optional<ExaminationEntity> findByTenantIdAndClientOfflineId(UUID tenantId, String clientOfflineId);
}
