package zw.gov.mohcc.impilo.oros.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.oros.persistence.entity.PrescriptionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<PrescriptionEntity, String> {

    Optional<PrescriptionEntity> findByTenantIdAndPrescriptionId(UUID tenantId, String prescriptionId);

    List<PrescriptionEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);

    /**
     * Claim-path load: pessimistic row lock so the server-side repeats counter
     * decrement is race-safe — two pharmacies presenting the same token serialise
     * here and the second sees the decremented truth (§13.2.3, defeats T11).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PrescriptionEntity p where p.prescriptionId = :prescriptionId")
    Optional<PrescriptionEntity> findForClaim(@Param("prescriptionId") String prescriptionId);
}
