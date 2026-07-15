package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedurePacuObservationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedurePacuObservationRepository extends JpaRepository<ProcedurePacuObservationEntity, UUID> {

    List<ProcedurePacuObservationEntity> findByEpisodeIdOrderBySeqAsc(UUID episodeId);

    int countByEpisodeId(UUID episodeId);

    Optional<ProcedurePacuObservationEntity> findFirstByEpisodeIdOrderBySeqDesc(UUID episodeId);
}
