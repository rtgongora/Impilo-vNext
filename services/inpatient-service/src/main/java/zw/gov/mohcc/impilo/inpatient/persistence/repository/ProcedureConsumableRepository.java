package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureConsumableEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureConsumableRepository extends JpaRepository<ProcedureConsumableEntity, UUID> {
    List<ProcedureConsumableEntity> findByEpisodeIdOrderByRecordedAtDesc(UUID episodeId);
}
