package zw.gov.mohcc.impilo.pharmacy.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.domain.PrescriptionStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PrescriptionEntity;

import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<PrescriptionEntity, UUID> {
    Page<PrescriptionEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(
            UUID tenantId, String patientCpid, Pageable pageable);

    Page<PrescriptionEntity> findByTenantIdAndPatientCpidAndStatusOrderByCreatedAtDesc(
            UUID tenantId, String patientCpid, PrescriptionStatus status, Pageable pageable);
}
