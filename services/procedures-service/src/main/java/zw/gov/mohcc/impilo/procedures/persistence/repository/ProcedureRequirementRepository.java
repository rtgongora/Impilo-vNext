package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procedures.persistence.entity.ProcedureRequirementEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureRequirementRepository extends JpaRepository<ProcedureRequirementEntity, UUID> {

    List<ProcedureRequirementEntity> findByDefinitionIdOrderByDisplayOrderAsc(UUID definitionId);
}
