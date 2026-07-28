package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procedures.persistence.entity.AftercareInstructionEntity;

import java.util.List;
import java.util.UUID;

public interface AftercareInstructionRepository extends JpaRepository<AftercareInstructionEntity, UUID> {
    List<AftercareInstructionEntity> findByTemplateIdOrderByDisplayOrderAsc(UUID templateId);
}
