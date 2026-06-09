package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureAnaesthesiaScoreEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureAnaesthesiaScoreRepository extends JpaRepository<ProcedureAnaesthesiaScoreEntity, UUID> {
    List<ProcedureAnaesthesiaScoreEntity> findByEpisodeIdOrderByRecordedAtDesc(UUID episodeId);

    List<ProcedureAnaesthesiaScoreEntity> findByEpisodeIdAndPhaseOrderByRecordedAtDesc(UUID episodeId, String phase);

    Optional<ProcedureAnaesthesiaScoreEntity> findFirstByEpisodeIdAndScoreTypeOrderByRecordedAtDesc(
            UUID episodeId, String scoreType);
}
