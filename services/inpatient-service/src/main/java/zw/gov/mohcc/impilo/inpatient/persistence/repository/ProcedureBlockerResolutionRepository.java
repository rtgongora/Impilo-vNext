package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureBlockerResolutionEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureBlockerResolutionRepository extends JpaRepository<ProcedureBlockerResolutionEntity, UUID> {

    List<ProcedureBlockerResolutionEntity> findByEpisodeIdOrderByCreatedAtDesc(UUID episodeId);
}
