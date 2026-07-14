package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureInstrumentSetUseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureInstrumentSetUseRepository
        extends JpaRepository<ProcedureInstrumentSetUseEntity, UUID> {

    List<ProcedureInstrumentSetUseEntity> findByEpisodeIdOrderBySeqAsc(UUID episodeId);

    int countByEpisodeId(UUID episodeId);

    Optional<ProcedureInstrumentSetUseEntity> findFirstByEpisodeIdAndTusoInstrumentSetIdOrderBySeqDesc(
            UUID episodeId, String tusoInstrumentSetId);

    List<ProcedureInstrumentSetUseEntity> findByEpisodeIdAndIssueStatus(UUID episodeId, String issueStatus);
}
