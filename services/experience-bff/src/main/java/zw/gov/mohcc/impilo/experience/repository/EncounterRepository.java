package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.Encounter;

import java.util.Optional;
import java.util.UUID;

public interface EncounterRepository extends JpaRepository<Encounter, UUID> {

    Page<Encounter> findByTenantIdAndPatientId(String tenantId, UUID patientId, Pageable pageable);

    Optional<Encounter> findByIdAndTenantId(UUID id, String tenantId);
}
