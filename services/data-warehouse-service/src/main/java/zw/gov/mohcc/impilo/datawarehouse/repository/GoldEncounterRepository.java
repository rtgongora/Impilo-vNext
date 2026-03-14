package zw.gov.mohcc.impilo.datawarehouse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datawarehouse.domain.GoldEncounterEntity;

import java.util.Optional;
import java.util.UUID;

public interface GoldEncounterRepository extends JpaRepository<GoldEncounterEntity, Long> {

    Optional<GoldEncounterEntity> findByTenantIdAndEncounterId(UUID tenantId, String encounterId);
}
