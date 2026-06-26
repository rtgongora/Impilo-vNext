package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdmissionRepository extends JpaRepository<AdmissionEntity, Long> {

    Optional<AdmissionEntity> findByAdmissionRef(UUID admissionRef);

    List<AdmissionEntity> findByFacilityIdAndStatus(UUID facilityId, String status);

    List<AdmissionEntity> findByFacilityId(UUID facilityId);

    List<AdmissionEntity> findByStatus(String status);

    List<AdmissionEntity> findBySubjectCpidAndFacilityIdAndStatus(
            String subjectCpid, UUID facilityId, String status);

    List<AdmissionEntity> findBySubjectCpid(String subjectCpid);

    List<AdmissionEntity> findBySubjectCpidAndStatus(String subjectCpid, String status);

    /**
     * Census admission materialised from a given PCT admission (tenant-scoped). The PCT<->inpatient
     * handshake is keyed on {@code pct_admission_id}; this is the idempotency lookup for
     * {@code admitFromPctApproval} and matches regardless of admission status (ADMITTED or DISCHARGED),
     * so a redelivered approval after discharge cannot create a second census row / double bed-day.
     */
    Optional<AdmissionEntity> findByTenantIdAndPctAdmissionId(UUID tenantId, UUID pctAdmissionId);
}
