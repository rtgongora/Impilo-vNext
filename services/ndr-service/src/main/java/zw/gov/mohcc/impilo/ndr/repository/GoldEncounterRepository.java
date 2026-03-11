package zw.gov.mohcc.impilo.ndr.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.ndr.domain.GoldEncounterEntity;

import java.util.UUID;

public interface GoldEncounterRepository extends JpaRepository<GoldEncounterEntity, UUID> {

    Page<GoldEncounterEntity> findByTenantIdAndPatientRef(UUID tenantId, String patientRef, Pageable pageable);

    Page<GoldEncounterEntity> findByTenantId(UUID tenantId, Pageable pageable);

    long countByTenantId(UUID tenantId);
}
