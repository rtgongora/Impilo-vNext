package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.GraduationRecordEntity;

public interface GraduationRecordRepository extends JpaRepository<GraduationRecordEntity, UUID> {

    List<GraduationRecordEntity> findByTenantIdAndStudentProfileIdOrderByCreatedAtDesc(UUID tenantId, UUID studentProfileId);
}
