package zw.gov.mohcc.impilo.pct.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyMedicationAdminEntity;

public interface EmergencyMedicationAdminRepository extends JpaRepository<EmergencyMedicationAdminEntity, UUID> {
    List<EmergencyMedicationAdminEntity> findByTenantIdAndEpisodeIdOrderByAdministeredAtDesc(UUID tenantId, UUID episodeId);
}
