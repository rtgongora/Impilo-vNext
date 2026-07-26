package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationEntity;

import java.util.List;
import java.util.UUID;

public interface MedicationReconciliationRepository extends JpaRepository<MedicationReconciliationEntity, UUID> {

    List<MedicationReconciliationEntity> findByTenantIdAndSubjectCpidOrderByStartedAtDesc(UUID tenantId, String subjectCpid);
}
