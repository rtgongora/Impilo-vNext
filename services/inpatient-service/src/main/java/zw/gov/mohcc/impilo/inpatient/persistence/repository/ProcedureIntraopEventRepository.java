package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureIntraopEventEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureIntraopEventRepository extends JpaRepository<ProcedureIntraopEventEntity, UUID> {
    List<ProcedureIntraopEventEntity> findByEpisodeIdOrderByRecordedAtAsc(UUID episodeId);
}
