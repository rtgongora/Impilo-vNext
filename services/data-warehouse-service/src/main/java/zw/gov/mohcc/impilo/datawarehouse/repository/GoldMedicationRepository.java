package zw.gov.mohcc.impilo.datawarehouse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datawarehouse.domain.GoldMedicationEntity;

import java.util.Optional;
import java.util.UUID;

public interface GoldMedicationRepository extends JpaRepository<GoldMedicationEntity, Long> {

    Optional<GoldMedicationEntity> findByTenantIdAndMedicationId(UUID tenantId, String medicationId);
}
