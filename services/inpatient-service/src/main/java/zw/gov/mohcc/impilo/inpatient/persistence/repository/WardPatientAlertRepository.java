package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardPatientAlertEntity;

import java.util.List;
import java.util.UUID;

public interface WardPatientAlertRepository extends JpaRepository<WardPatientAlertEntity, UUID> {
    List<WardPatientAlertEntity> findByTenantIdAndWardIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, UUID wardId, String status);

    List<WardPatientAlertEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(
            UUID tenantId, String subjectCpid);
}
