package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.VitalsEntity;

import java.util.UUID;

public interface VitalsRepository extends JpaRepository<VitalsEntity, Long> {

    Page<VitalsEntity> findByTenantIdAndPatientIdOrderByRecordedAtDesc(
            UUID tenantId, String patientId, Pageable pageable);

    Page<VitalsEntity> findByTenantIdAndEncounterIdOrderByRecordedAtDesc(
            UUID tenantId, String encounterId, Pageable pageable);
}
