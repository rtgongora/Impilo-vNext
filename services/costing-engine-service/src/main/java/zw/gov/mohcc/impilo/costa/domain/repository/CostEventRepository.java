package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.CostEventEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CostEventRepository extends JpaRepository<CostEventEntity, String> {

    List<CostEventEntity> findTop200ByTenantIdAndEncounterIdOrderByCreatedAtDesc(UUID tenantId, String encounterId);

    List<CostEventEntity> findTop200ByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);
}
