package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.TreatmentRegimenEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentRegimenRepository extends JpaRepository<TreatmentRegimenEntity, UUID> {

    List<TreatmentRegimenEntity> findByTenantIdAndEnrolmentIdOrderByStartedOnDesc(
            UUID tenantId, UUID enrolmentId);

    /** The current regimen for an enrolment — the partial-unique constraint guarantees ≤1 open row. */
    Optional<TreatmentRegimenEntity> findByTenantIdAndEnrolmentIdAndEndedOnIsNull(
            UUID tenantId, UUID enrolmentId);
}
